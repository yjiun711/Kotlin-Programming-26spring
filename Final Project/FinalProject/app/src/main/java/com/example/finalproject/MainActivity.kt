package com.example.finalproject

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

// ==================== 1. 核心領域模型 ====================

enum class Suit(val symbol: String) { SPADES("♠"), HEARTS("♥") }
enum class Rank(val value: Int, val label: String) {
    R7(7, "7"), R8(8, "8"), R9(9, "9"), R10(10, "10"),
    J(11, "J"), Q(12, "Q"), K(13, "K"), A(14, "A")
}
enum class HandRank(val power: Int, val label: String) {
    HIGH_CARD(1, "High Card"), ONE_PAIR(2, "One Pair"), TWO_PAIRS(3, "Two Pairs"),
    THREE_OF_A_KIND(4, "Three of a Kind"), STRAIGHT(5, "Straight"), FLUSH(6, "Flush"),
    FULL_HOUSE(7, "Full House"), FOUR_OF_A_KIND(8, "Four of a Kind"),
    STRAIGHT_FLUSH(9, "Straight Flush"), FIVE_OF_A_KIND(10, "Five of a Kind"), ROYAL_FLUSH(11, "Royal Flush")
}
data class Card(val suit: Suit, val rank: Rank) {
    override fun toString(): String = "${suit.symbol}${rank.label}"
}

class Deck {
    private val cards = mutableListOf<Card>()
    init {
        for (i in 1..7) {
            for (suit in Suit.values()) {
                for (rank in Rank.values()) { cards.add(Card(suit, rank)) }
            }
        }
    }
    fun shuffle() { cards.shuffle() }
    fun draw(count: Int): List<Card> {
        val drawn = cards.take(count)
        cards.removeAll(drawn)
        return drawn
    }
}

// ==================== 2. UI 狀態結構設計 ====================

data class PlayerState(
    val name: String,
    var money: Int = 50,
    var holeCards: List<Card> = emptyList(),
    var isFolded: Boolean = false,
    var isAllIn: Boolean = false,
    var betAmount: Int = 0,
    var bestHandRank: HandRank = HandRank.HIGH_CARD,
    val isHuman: Boolean = false,
    var lastActionDescription: String = "準備中"
)

enum class GameStage { INITIAL, BETTING, SHOWDOWN, GAME_OVER }

// ==================== 3. 牌力評估 ====================

object HandEvaluator {
    fun evaluateBestHand(holeCards: List<Card>, communityCards: List<Card>): HandRank {
        if (holeCards.isEmpty() || communityCards.size < 3) return HandRank.HIGH_CARD
        val communityCombos = getCombinations(communityCards, 3)
        var bestRank = HandRank.HIGH_CARD
        for (combo in communityCombos) {
            val hand = holeCards + combo
            val currentRank = evaluate5Cards(hand)
            if (currentRank.power > bestRank.power) { bestRank = currentRank }
        }
        return bestRank
    }

    private fun getCombinations(list: List<Card>, k: Int): List<List<Card>> {
        if (k == 0) return listOf(emptyList())
        if (list.isEmpty()) return emptyList()
        val head = list.first()
        val tail = list.drop(1)
        val withHead = getCombinations(tail, k - 1).map { it + head }
        val withoutHead = getCombinations(tail, k)
        return withHead + withoutHead
    }

    private fun evaluate5Cards(cards: List<Card>): HandRank {
        val isFlush = cards.all { it.suit == cards.first().suit }
        val sortedRanks = cards.map { it.rank.value }.sortedDescending()
        val isStraight = (0..3).all { sortedRanks[it] - sortedRanks[it + 1] == 1 }
        val rankCounts = cards.groupBy { it.rank }.mapValues { it.value.size }.values.sortedDescending()

        return when {
            isFlush && isStraight && sortedRanks.first() == 14 -> HandRank.ROYAL_FLUSH
            rankCounts == listOf(5) -> HandRank.FIVE_OF_A_KIND
            isFlush && isStraight -> HandRank.STRAIGHT_FLUSH
            rankCounts == listOf(4, 1) -> HandRank.FOUR_OF_A_KIND
            rankCounts == listOf(3, 2) -> HandRank.FULL_HOUSE
            isFlush -> HandRank.FLUSH
            isStraight -> HandRank.STRAIGHT
            rankCounts == listOf(3, 1, 1) -> HandRank.THREE_OF_A_KIND
            rankCounts == listOf(2, 2, 1) -> HandRank.TWO_PAIRS
            rankCounts == listOf(2, 1, 1, 1) -> HandRank.ONE_PAIR
            else -> HandRank.HIGH_CARD
        }
    }
}

// ==================== 4. SVG 卡牌渲染元件 ====================

@Composable
fun PlayingCardView(card: Card?, isFaceUp: Boolean, width: Dp, height: Dp, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val resName = if (isFaceUp && card != null) {
        val suitLetter = when (card.suit) {
            Suit.SPADES -> "s"
            Suit.HEARTS -> "h"
        }
        val rankStr = card.rank.label.lowercase()
        "card_$suitLetter$rankStr"
    } else {
        "card_back"
    }

    val resId = context.resources.getIdentifier(resName, "drawable", context.packageName)

    if (resId != 0) {
        Image(
            painter = painterResource(id = resId),
            contentDescription = card?.toString() ?: "蓋牌",
            modifier = modifier
                .width(width)
                .height(height)
                .padding(1.dp)
        )
    } else {
        Text(
            text = if (isFaceUp && card != null) card.toString() else "🂠",
            style = MaterialTheme.typography.bodyLarge,
            modifier = modifier.padding(2.dp)
        )
    }
}

// ==================== 5. Android 主控制與介面 ====================

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MaterialTheme {
                Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
                    TexasHoldemGameScreen()
                }
            }
        }
    }
}

@Composable
fun TexasHoldemGameScreen() {
    val coroutineScope = rememberCoroutineScope()
    val scrollState = rememberScrollState()

    var roundCount by remember { mutableStateOf(1) }
    var gameStage by remember { mutableStateOf(GameStage.INITIAL) }
    var communityCards by remember { mutableStateOf<List<Card>>(emptyList()) }
    var gameLogs by remember { mutableStateOf<List<String>>(listOf("歡迎來到自定義德州撲克對戰！")) }
    var customBetInput by remember { mutableStateOf("") }
    var isSimulating by remember { mutableStateOf(false) }

    // 控制最終結果彈出視窗的狀態變數
    var showRankingDialog by remember { mutableStateOf(false) }

    var players by remember {
        mutableStateOf(
            listOf(
                PlayerState("電腦1", isHuman = false),
                PlayerState("電腦2", isHuman = false),
                PlayerState("電腦3", isHuman = false),
                PlayerState("玩家", isHuman = true)
            )
        )
    }

    fun appendLog(msg: String) { gameLogs = gameLogs + msg }

    fun determineAIAction(name: String, money: Int, rank: HandRank): String {
        return when (name) {
            "電腦1" -> {
                if (money < 10) "ALL_IN"
                else if (rank.power < HandRank.TWO_PAIRS.power) "FOLD"
                else if (rank.power == HandRank.TWO_PAIRS.power) "10"
                else "ALL_IN"
            }
            "電腦2" -> {
                if (money < 20) "ALL_IN"
                else if (rank.power < HandRank.TWO_PAIRS.power) "FOLD"
                else if (rank.power == HandRank.TWO_PAIRS.power) "20"
                else "ALL_IN"
            }
            "電腦3" -> {
                if (money < 30) "ALL_IN"
                else if (rank.power < HandRank.THREE_OF_A_KIND.power) "FOLD"
                else if (rank.power == HandRank.THREE_OF_A_KIND.power) "30"
                else "ALL_IN"
            }
            else -> "FOLD"
        }
    }

    fun startNewRound() {
        isSimulating = true
        val updatedPlayers = players.map { it.copy() }
        updatedPlayers.forEach {
            it.lastActionDescription = "思考中..."
        }
        players = updatedPlayers

        coroutineScope.launch {
            var localComm: List<Card> = emptyList()
            val localPlayersCards = mutableListOf<List<Card>>()

            if (roundCount == 4) {
                withContext(Dispatchers.Default) {
                    var validRiggedBoard = false
                    while (!validRiggedBoard) {
                        val testDeck = Deck()
                        testDeck.shuffle()
                        val testHoles = List(4) { testDeck.draw(2) }
                        val testComm = testDeck.draw(5)

                        val aiStates = updatedPlayers.take(3).mapIndexed { index, ai ->
                            val rank = HandEvaluator.evaluateBestHand(testHoles[index], testComm)
                            val action = determineAIAction(ai.name, ai.money, rank)
                            Pair(action, rank)
                        }
                        val allInAIs = aiStates.filter { it.first == "ALL_IN" }
                        if (allInAIs.size >= 2) {
                            val ranks = allInAIs.map { it.second.power }.distinct()
                            if (ranks.size > 1) {
                                validRiggedBoard = true
                                localComm = testComm
                                localPlayersCards.addAll(testHoles)
                            }
                        }
                    }
                }
            } else {
                val deck = Deck()
                deck.shuffle()
                localPlayersCards.addAll(List(4) { deck.draw(2) })
                localComm = deck.draw(5)
            }

            val nextPlayers = players.mapIndexed { index, p ->
                p.copy().apply {
                    holeCards = localPlayersCards[index]
                    bestHandRank = HandEvaluator.evaluateBestHand(localPlayersCards[index], localComm)
                }
            }

            for (i in 0..2) {
                val ai = nextPlayers[i]
                val action = determineAIAction(ai.name, ai.money, ai.bestHandRank)
                ai.lastActionDescription = action
            }
            nextPlayers[3].lastActionDescription = "等待你的選擇..."

            players = nextPlayers
            communityCards = localComm
            gameStage = GameStage.BETTING
            isSimulating = false
            appendLog("--- 第 $roundCount 回合發牌完畢，請進行下注 ---")
        }
    }

    fun executeSettlement(humanAction: String) {
        val nextPlayers = players.map { it.copy() }
        nextPlayers[3].lastActionDescription = humanAction

        var anyAllIn = false
        val intents = nextPlayers.map { it.lastActionDescription }

        if (intents.any { it == "ALL_IN" }) anyAllIn = true

        var pot = 0
        val activePlayers = mutableListOf<PlayerState>()

        if (anyAllIn) {
            appendLog("[系統] 偵測到有人 All-in！僅保留 All-in 玩家比牌，其餘棄牌。")
            nextPlayers.forEach { p ->
                if (p.lastActionDescription == "ALL_IN") {
                    p.isAllIn = true
                    p.betAmount = p.money
                    pot += p.betAmount
                    p.money = 0
                    activePlayers.add(p)
                } else {
                    p.isFolded = true
                    p.lastActionDescription = "FOLD"
                }
            }
        } else {
            val activeBeforeSettlement = nextPlayers.filter { it.lastActionDescription != "FOLD" }
            if (activeBeforeSettlement.isEmpty()) {
                appendLog("所有人都棄牌，本局無贏家。")
                gameStage = GameStage.SHOWDOWN
                players = nextPlayers
                return
            }

            val maxBet = activeBeforeSettlement.map {
                if (it.lastActionDescription.toIntOrNull() != null) it.lastActionDescription.toInt() else 0
            }.maxOrNull() ?: 0

            nextPlayers.forEach { p ->
                if (p.lastActionDescription == "FOLD") {
                    p.isFolded = true
                } else {
                    val actualBet = minOf(p.money, maxBet)
                    p.betAmount = actualBet
                    p.money -= actualBet
                    pot += actualBet
                    activePlayers.add(p)
                }
            }
        }

        if (activePlayers.isNotEmpty()) {
            val maxPower = activePlayers.maxOf { it.bestHandRank.power }
            val winners = activePlayers.filter { it.bestHandRank.power == maxPower }
            val winAmount = pot / winners.size

            appendLog("底池總計: $pot 元。")
            activePlayers.forEach { appendLog("${it.name}: 展示 [${it.bestHandRank.label}]") }

            if (winners.size > 1) {
                appendLog("平手！由 ${winners.joinToString { it.name }} 均分，各得 $winAmount 元（餘數交由賭場）。")
            } else {
                appendLog("贏家是 ${winners.first().name}，獨得 $winAmount 元！")
            }

            nextPlayers.forEach { p ->
                if (winners.any { it.name == p.name }) { p.money += winAmount }
            }
        }

        players = nextPlayers

        val hasPlayerConditionMet = nextPlayers.any { it.money <= 0 || it.money >= 100 }
        if (hasPlayerConditionMet || roundCount >= 4) {
            gameStage = GameStage.GAME_OVER
            appendLog("=== 滿足結束條件，遊戲正式終結 ===")
        } else {
            gameStage = GameStage.SHOWDOWN
        }
    }

    fun restartWholeGame() {
        players = listOf(
            PlayerState("電腦1", isHuman = false),
            PlayerState("電腦2", isHuman = false),
            PlayerState("電腦3", isHuman = false),
            PlayerState("玩家", isHuman = true)
        )
        roundCount = 1
        communityCards = emptyList()
        gameStage = GameStage.INITIAL
        showRankingDialog = false
        gameLogs = listOf("遊戲重新開始！")
    }

    Column(modifier = Modifier.padding(12.dp).fillMaxSize()) {
        Text("德州撲克變體賽（第 $roundCount 回合）", style = MaterialTheme.typography.titleLarge)
        Spacer(modifier = Modifier.height(6.dp))

        Column(modifier = Modifier.weight(1f).verticalScroll(scrollState)) {

            Card(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
                Column(modifier = Modifier.padding(10.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("公共牌 (選3張)", style = MaterialTheme.typography.titleMedium)
                    Spacer(modifier = Modifier.height(6.dp))
                    if (communityCards.isEmpty()) {
                        Text("尚未發牌", style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.outline)
                    } else {
                        Row(horizontalArrangement = Arrangement.Center, modifier = Modifier.fillMaxWidth()) {
                            communityCards.forEach { card ->
                                PlayingCardView(card = card, isFaceUp = true, width = 64.dp, height = 96.dp)
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(4.dp))

            Text("電腦對手狀態", style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.secondary)
            players.filter { !it.isHuman }.forEach { ai ->
                Card(
                    modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
                ) {
                    Row(
                        modifier = Modifier.padding(6.dp).fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text("${ai.name} — 資金: ${ai.money} 元", style = MaterialTheme.typography.bodyMedium)
                            Text("動作: ${ai.lastActionDescription}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.outline)
                        }
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            if (ai.holeCards.isNotEmpty()) {
                                // 攤牌階段或遊戲結束時，皆顯示正面與牌力
                                val showAiCards = gameStage == GameStage.SHOWDOWN || gameStage == GameStage.GAME_OVER

                                if (showAiCards) {
                                    Text(
                                        text = "[${ai.bestHandRank.label}]",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.padding(end = 6.dp)
                                    )
                                }

                                ai.holeCards.forEach { card ->
                                    PlayingCardView(card = card, isFaceUp = showAiCards, width = 36.dp, height = 54.dp)
                                }
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(6.dp))

            val human = players.find { it.isHuman }!!
            Text("玩家專區 (底牌必須全選)", style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary)
            Card(
                modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)
            ) {
                Column(modifier = Modifier.padding(12.dp)) {
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                        Text("${human.name} 資金: ${human.money} 元", style = MaterialTheme.typography.titleMedium)
                        Text(human.lastActionDescription, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.primary)
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.Center, modifier = Modifier.fillMaxWidth()) {
                        if (human.holeCards.isNotEmpty()) {
                            Row {
                                human.holeCards.forEach { card ->
                                    PlayingCardView(card = card, isFaceUp = true, width = 78.dp, height = 117.dp)
                                }
                            }
                            Spacer(modifier = Modifier.width(16.dp))
                            Surface(
                                shape = MaterialTheme.shapes.small,
                                color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.1f),
                                modifier = Modifier.padding(4.dp)
                            ) {
                                Text(
                                    text = human.bestHandRank.label,
                                    style = MaterialTheme.typography.titleMedium,
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                                    color = MaterialTheme.colorScheme.onPrimaryContainer
                                )
                            }
                        } else {
                            Text("等待發牌...", style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.outline)
                        }
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(6.dp))
        Text("對局日誌:", style = MaterialTheme.typography.titleSmall)
        Card(
            modifier = Modifier.height(85.dp).fillMaxWidth().padding(vertical = 2.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
        ) {
            LazyColumn(modifier = Modifier.fillMaxSize().padding(4.dp)) {
                items(gameLogs.reversed()) { log ->
                    Text(log, style = MaterialTheme.typography.bodySmall)
                }
            }
        }

        Spacer(modifier = Modifier.height(4.dp))

        // 底部狀態按鈕面板
        when (gameStage) {
            GameStage.INITIAL -> {
                Button(
                    onClick = { startNewRound() },
                    modifier = Modifier.fillMaxWidth(),
                    enabled = !isSimulating
                ) {
                    Text(if (isSimulating) "正在密謀盤面中..." else "開始本回合發牌")
                }
            }
            GameStage.BETTING -> {
                Column {
                    // 1. 狀態偵測：檢查是否已經有電腦選擇 ALL_IN
                    val isAnyAiAllIn = players.take(3).any { it.lastActionDescription == "ALL_IN" }

                    // 2. 條件渲染：如果有，則顯示提示並封鎖自選金額
                    if (isAnyAiAllIn) {
                        Surface(
                            color = MaterialTheme.colorScheme.errorContainer,
                            shape = MaterialTheme.shapes.small,
                            modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp)
                        ) {
                            Text(
                                text = "⚠️ 由於已有對手選擇 All-in，你僅能選擇「全部投入」跟進，或「棄牌」保身。",
                                color = MaterialTheme.colorScheme.onErrorContainer,
                                style = MaterialTheme.typography.bodyMedium,
                                modifier = Modifier.padding(12.dp)
                            )
                        }
                    }

                    // 基礎二選一按鈕（永遠顯示）
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Button(onClick = { executeSettlement("FOLD") }, modifier = Modifier.weight(1f).padding(end = 4.dp)) { Text("棄牌 (FOLD)") }
                        Button(onClick = { executeSettlement("ALL_IN") }, modifier = Modifier.weight(1f).padding(start = 4.dp)) { Text("全部投入 (ALL_IN)") }
                    }
                    Spacer(modifier = Modifier.height(4.dp))

                    // 3. 條件渲染：只有在沒有人 ALL_IN 的安全情況下，才開放自選輸入框
                    if (!isAnyAiAllIn) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            OutlinedTextField(
                                value = customBetInput,
                                onValueChange = { customBetInput = it },
                                label = { Text("輸入自訂下注金額") },
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                modifier = Modifier.weight(1f).height(56.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Button(
                                onClick = {
                                    val bet = customBetInput.toIntOrNull()
                                    val humanMoney = players.find { it.isHuman }?.money ?: 0
                                    if (bet != null && bet > 0 && bet <= humanMoney) {
                                        executeSettlement(bet.toString())
                                        customBetInput = ""
                                    }
                                },
                                modifier = Modifier.height(48.dp)
                            ) { Text("確認") }
                        }
                    }
                }
            }
            GameStage.SHOWDOWN -> {
                Button(
                    onClick = {
                        communityCards = emptyList()
                        val clearedPlayers = players.map { it.copy() }
                        clearedPlayers.forEach {
                            it.holeCards = emptyList()
                            it.bestHandRank = HandRank.HIGH_CARD
                            it.lastActionDescription = "準備中"
                        }
                        players = clearedPlayers

                        roundCount++
                        gameStage = GameStage.INITIAL
                    },
                    modifier = Modifier.fillMaxWidth()
                ) { Text("進入第 ${roundCount + 1} 回合發牌") }
            }
            GameStage.GAME_OVER -> {
                Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth()) {
                    Text("遊戲結束！", color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.titleMedium)
                    Spacer(modifier = Modifier.height(8.dp))
                    Button(
                        onClick = { showRankingDialog = true },
                        modifier = Modifier.fillMaxWidth()
                    ) { Text("顯示結果") }
                }
            }
        }
    }

    // 彈出式結果與排名視窗
    if (showRankingDialog) {
        AlertDialog(
            onDismissRequest = { /* 強制必須點擊按鈕關閉 */ },
            title = {
                Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                    Text("【 最終對局排名 】", style = MaterialTheme.typography.titleLarge, color = MaterialTheme.colorScheme.error)
                }
            },
            text = {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp)
                ) {
                    val sortedPlayers = players.sortedByDescending { it.money }
                    var currentRank = 1
                    sortedPlayers.forEachIndexed { index, p ->
                        if (index > 0 && p.money < sortedPlayers[index - 1].money) {
                            currentRank = index + 1
                        }
                        Text(
                            text = "第 ${currentRank} 名: ${p.name}  (${p.money} 元)",
                            style = MaterialTheme.typography.bodyLarge,
                            modifier = Modifier.padding(vertical = 6.dp)
                        )
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = { restartWholeGame() },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("重新開始新遊戲")
                }
            }
        )
    }
}