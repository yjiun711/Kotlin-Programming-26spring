//111652042 顏友君
import java.util.Scanner
import kotlin.system.exitProcess

enum class Suit(val symbol: String) {
    SPADES("♠"), HEARTS("♥")
}

enum class Rank(val value: Int, val label: String) {
    R7(7, "7"), R8(8, "8"), R9(9, "9"), R10(10, "10"),
    J(11, "J"), Q(12, "Q"), K(13, "K"), A(14, "A")
}

enum class HandRank(val power: Int, val label: String) {
    HIGH_CARD(1, "High Card"),
    ONE_PAIR(2, "One Pair"),
    TWO_PAIRS(3, "Two Pairs"),
    THREE_OF_A_KIND(4, "Three of a Kind"),
    STRAIGHT(5, "Straight"),
    FLUSH(6, "Flush"),
    FULL_HOUSE(7, "Full House"),
    FOUR_OF_A_KIND(8, "Four of a Kind"),
    STRAIGHT_FLUSH(9, "Straight Flush"),
    FIVE_OF_A_KIND(10, "Five of a Kind"),
    ROYAL_FLUSH(11, "Royal Flush")
}

data class Card(val suit: Suit, val rank: Rank) {
    override fun toString(): String = "${suit.symbol}${rank.label}"
}

class Deck {
    private val cards = mutableListOf<Card>()

    init {
        for (i in 1..7) {
            for (suit in Suit.values()) {
                for (rank in Rank.values()) {
                    cards.add(Card(suit, rank))
                }
            }
        }
    }

    fun shuffle() {
        cards.shuffle()
    }

    fun draw(count: Int): List<Card> {
        val drawn = cards.take(count)
        cards.removeAll(drawn)
        return drawn
    }
}

class Player(val name: String, var money: Int, val isHuman: Boolean) {
    var holeCards: List<Card> = emptyList()
    var isFolded: Boolean = false
    var isAllIn: Boolean = false
    var betAmount: Int = 0
    var bestHandRank: HandRank = HandRank.HIGH_CARD

    fun resetForRound() {
        holeCards = emptyList()
        isFolded = false
        isAllIn = false
        betAmount = 0
        bestHandRank = HandRank.HIGH_CARD
    }
}

object HandEvaluator {
    fun evaluateBestHand(holeCards: List<Card>, communityCards: List<Card>): HandRank {
        val communityCombos = getCombinations(communityCards, 3)
        var bestRank = HandRank.HIGH_CARD

        for (combo in communityCombos) {
            val hand = holeCards + combo
            val currentRank = evaluate5Cards(hand)
            if (currentRank.power > bestRank.power) {
                bestRank = currentRank
            }
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

class Game {
    private val scanner = Scanner(System.`in`)
    private var players = listOf(
        Player("電腦1", 50, false),
        Player("電腦2", 50, false),
        Player("電腦3", 50, false),
        Player("玩家", 50, true)
    )
    private var roundCount = 1

    fun start() {
        while (true) {
            playRound()

            if (checkGameOver()) {
                printRankings()
                println("\n是否重新開始遊戲？(Y/N)")
                val input = scanner.next().uppercase()
                if (input == "Y") {
                    resetGame()
                    // 執行重置後，跳過此次迴圈剩餘部分，直接從 while 開頭執行第一回合
                    continue
                } else {
                    println("遊戲結束。")
                    exitProcess(0)
                }
            }

            // 只有在遊戲未達成結束條件時，才會執行到這裡並進入下一回合
            roundCount++
        }
    }

    private fun resetGame() {
        players.forEach { it.money = 50 }
        roundCount = 1
        println("\n=== 遊戲重新開始 ===")
    }

    private fun checkGameOver(): Boolean {
        return players.any { it.money <= 0 || it.money >= 100 }
    }

    private fun playRound() {
        println("\n--- 第 $roundCount 回合開始 ---")
        players.forEach { it.resetForRound() }

        var communityCards: List<Card> = emptyList()

        if (roundCount == 4) {
            println("[系統] 第四回合：生成強制收斂盤面中，請稍候...")
            var validRiggedBoard = false
            var attempts = 0

            while (!validRiggedBoard) {
                attempts++

                // 每次模擬失敗，都必須重新建立一副完整 112 張的牌組
                val testDeck = Deck()
                testDeck.shuffle()

                val testHoles = players.map { testDeck.draw(2) }
                val testComm = testDeck.draw(5)

                val aiStates = players.take(3).mapIndexed { index, ai ->
                    val rank = HandEvaluator.evaluateBestHand(testHoles[index], testComm)
                    val action = determineAIAction(ai.name, ai.money, rank)
                    Pair(action, rank)
                }

                val allInAIs = aiStates.filter { it.first == "ALL_IN" }

                // 標準條件：至少兩個 AI 觸發 All-in，且兩者牌力不同
                if (allInAIs.size >= 2) {
                    val ranks = allInAIs.map { it.second.power }.distinct()
                    if (ranks.size > 1) {
                        validRiggedBoard = true
                        players.forEachIndexed { index, p -> p.holeCards = testHoles[index] }
                        communityCards = testComm
                    }
                }
            }
        } else {
            // 前三回合：正常建立一副牌，洗牌並發牌
            val deck = Deck()
            deck.shuffle()
            players.forEach { it.holeCards = deck.draw(2) }
            communityCards = deck.draw(5)
        }

        // 重新計算所有玩家最終鎖定的最佳牌力
        players.forEach { p ->
            p.bestHandRank = HandEvaluator.evaluateBestHand(p.holeCards, communityCards)
        }

        println("公共牌: $communityCards")
        val human = players.find { it.isHuman }!!
        println("你的底牌: ${human.holeCards} | 當前資金: ${human.money} | 目前最佳牌力: ${human.bestHandRank.label}")

        // --- 以下為下注與結算邏輯 ---
        var anyAllIn = false
        val intents = mutableMapOf<Player, String>()

        for (player in players) {
            if (player.isHuman) {
                intents[player] = getHumanAction(player)
            } else {
                val action = determineAIAction(player.name, player.money, player.bestHandRank)
                intents[player] = action
                println("${player.name} 選擇了: $action")
            }
            if (intents[player] == "ALL_IN") anyAllIn = true
        }

        var pot = 0
        val activePlayers = mutableListOf<Player>()

        if (anyAllIn) {
            println("\n[結算資訊] 有人 All-in，僅 All-in 玩家繼續比牌，其餘視同棄牌(無損失)。")
            for (player in players) {
                if (intents[player] == "ALL_IN") {
                    player.isAllIn = true
                    player.betAmount = player.money
                    pot += player.betAmount
                    player.money = 0
                    activePlayers.add(player)
                } else {
                    player.isFolded = true
                }
            }
        } else {
            val nonFolded = players.filter { intents[it] != "FOLD" }
            if (nonFolded.isEmpty()) {
                println("\n所有人都棄牌，本局無人獲勝。")
                return
            }
            val maxBet = nonFolded.map { intents[it]!!.toInt() }.maxOrNull() ?: 0
            println("\n[結算資訊] 無人 All-in，跟注最大金額為 $maxBet。")

            for (player in players) {
                if (intents[player] == "FOLD") {
                    player.isFolded = true
                } else {
                    val actualBet = minOf(player.money, maxBet)
                    player.betAmount = actualBet
                    player.money -= actualBet
                    pot += actualBet
                    activePlayers.add(player)
                }
            }
        }

        println("\n--- 攤牌結算 ---")
        if (activePlayers.isEmpty()) return

        val maxPower = activePlayers.maxOf { it.bestHandRank.power }
        val winners = activePlayers.filter { it.bestHandRank.power == maxPower }

        activePlayers.forEach { p ->
            println("${p.name}: 牌力 [${p.bestHandRank.label}] (底牌: ${p.holeCards})")
        }

        val winAmount = pot / winners.size
        println("\n總獎池: $pot")
        if (winners.size > 1) {
            println("平手！${winners.joinToString(", ") { it.name }} 平分獎池，每人贏得 $winAmount。小數點由賭場收走。")
        } else {
            println("贏家是 ${winners.first().name}，贏得全部獎池 $winAmount！")
        }

        winners.forEach { it.money += winAmount }

        println("\n--- 結算後資金 ---")
        players.forEach { println("${it.name}: ${it.money} 元") }
    }

    private fun determineAIAction(name: String, money: Int, rank: HandRank): String {
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

    private fun getHumanAction(player: Player): String {
        while (true) {
            println("\n輪到你行動。請輸入動作 (FOLD / ALL_IN / 輸入下注數字):")
            val input = scanner.next().uppercase()
            when {
                input == "FOLD" || input == "ALL_IN" -> return input
                input.toIntOrNull() != null -> {
                    val bet = input.toInt()
                    if (bet <= 0) println("下注必須大於 0。")
                    else if (bet > player.money) println("資金不足，你只剩 ${player.money}。請選擇 ALL_IN 或較小金額。")
                    else return bet.toString()
                }
                else -> println("無效輸入，請重試。")
            }
        }
    }

    private fun printRankings() {
        println("\n========== 遊戲結束 ==========")
        val ranked = players.sortedByDescending { it.money }
        ranked.forEachIndexed { index, player ->
            println("第 ${index + 1} 名: ${player.name} - 最終資金: ${player.money} 元")
        }
        println("==============================")
    }
}

fun main() {
    println("歡迎來到自定義德州撲克對戰！")
    Game().start()
}