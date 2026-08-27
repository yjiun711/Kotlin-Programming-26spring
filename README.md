# 簡化版德州撲克 (Texas Hold'em) - Kotlin 專案

本專案使用 **Kotlin** 實作特殊規則之簡化版德州撲克，依序完成了 **CLI 文字終端機版本** 以及 **Android 手機應用版本**。

---

## 專案規格與核心規則

### 1. 牌組與組牌限制
- **牌組構成**：使用 7 副牌，僅包含黑桃（♠）、紅心（♥）兩種花色，點數為 `7` 至 `A`，共計 $7 \times 2 \times 8 = 112$ 張牌。
- **組牌要求**：每位玩家獲發 2 張底牌與 5 張公共牌，**強制使用 2 張底牌搭配 5 張公共牌中的任選 3 張**（共 10 種組合）評估最佳牌力。
- **牌力大小**：`Royal Flush` > `Five of a Kind` > `Straight Flush` > `Four of a Kind` > `Full House` > `Flush` > `Straight` > `Three of a Kind` > `Two Pairs` > `One Pair` > `High Card`。

### 2. 下注與底池結算機制
- 每位玩家起始籌碼為 **50 元**。
- 下注順序固定為：**電腦1 $\rightarrow$ 電腦2 $\rightarrow$ 電腦3 $\rightarrow$ 人類玩家**。
- **All-in**：若有玩家 All-in，該局**僅有 All-in 玩家參與比牌**，其餘玩家視同棄牌且不損失籌碼。
- **一般下注 (Normal Bet)**：在無人 All-in 情況下，未棄牌玩家需以該輪**最高的下注金額**作為賭注進入比牌。
- **底池平分**：獲勝者拿走全部底池；若平手則均分，小數點餘額由系統收回。

### 3. 電腦下注規則 (AI Policy)
- **電腦1**：籌碼 < 10 則 All-in；牌力 < Two Pairs 棄牌；= Two Pairs 下注 10 元；> Two Pairs 則 All-in。
- **電腦2**：籌碼 < 20 則 All-in；牌力 < Two Pairs 棄牌；= Two Pairs 下注 20 元；> Two Pairs 則 All-in。
- **電腦3**：籌碼 < 30 則 All-in；牌力 < Three of a Kind 棄牌；= Three of a Kind 下注 30 元；> Three of a Kind 則 All-in。

### 4. 勝負判定與四回合終局保證
- **結束條件**：任一玩家籌碼超過 100 元或歸零（含負數）時遊戲結束，並輸出排名與重玩選項。
- **發牌演算機制**：
  - 前三回合採用完全隨機洗牌發牌。
  - **第四回合**：系統於發牌時進行模擬驗證，若隨機盤面無法造成「至少一名電腦 All-in 且最終輸光破產」，則重新發牌，確保遊戲在四回合以內必然結束。
