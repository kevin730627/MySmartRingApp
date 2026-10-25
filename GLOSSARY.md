# 詞彙表

MySmartRingApp 的領域語言。只記錄名詞定義，不記錄實作細節。

## 設備

**Ring（戒指）**
使用者佩戴、產生健康數據的智慧戒指（FutureLab PulseRing）。本 app 只服務一枚戒指。
_避免使用_：device、wearable

**Paired Ring（已配對戒指）**
使用者在本 app 中選定並記住的那一枚戒指。同一時間只有一枚。使用者可以解除配對，此時 app 只忘記這枚戒指，已儲存的資料不受影響。

**原廠 App**
戒指隨附的 FixFate app。本 app 要取代它的角色。

## 數據

**Metric（指標）**
一種被量測的健康項目。MVP 範圍：心率、血氧、睡眠。步數、HRV 與體溫列為之後。

**Reading（量測值）**
某個 Metric 在某個時間點的一筆數值。睡眠不是 Reading，而是 Sleep Session。

**Sleep Session（睡眠區間）**
從入睡到醒來的一段連續睡眠，內含深睡、淺睡、REM 的時間片段。歸屬於「醒來那天」。是與 Reading 並列的獨立概念。戒指可能把一晚切成多段 Sleep Session，各段分開保存，不合併。

**Sleep Stage（睡眠階段）**
Sleep Session 內的一個時間片段所處的狀態：深睡、淺睡、REM。戒指不回報清醒階段。

**Sleep Night（一晚睡眠）**
醒來在同一天的所有 Sleep Session，依入睡時間排列。只是顯示與統計上的分組，不是資料庫裡合併後的紀錄。

**Awake Gap（清醒時段）**
同一個 Sleep Night 中，相鄰兩段 Sleep Session 之間的空檔。戒指本身不回報清醒，所以清醒時段只出現在分段處，不屬於任何 Sleep Stage，也不計入睡眠時間。

**Raw Frame（原始封包）**
戒指在 Sync 時傳來的原始位元組。與解析後的 Reading、Sleep Session 一併保存，使解析錯誤能事後修正。

**Ring Time（戒指時間）**
戒指自己的時鐘。每次 Sync 時以手機時間校正，Reading 的時間點以校正後的 Ring Time 為準。

**Rebuild（重建）**
清除所有由 Raw Frame 衍生的 Reading 與 Sleep Session，再依最新解析規則重新計算。

**Sync（同步）**
使用者開啟 app 時，把戒指上尚未取得的 Reading 抓進本 app 資料庫的動作。目前不在背景進行。

## 呈現

**Dashboard（儀表板）**
使用者自訂的首頁，由多張 Card 組成。

**Card（卡片）**
儀表板上顯示單一 Metric 的區塊。使用者可選擇顯示哪些、調整順序、選擇時間範圍。

**Export（匯出）**
把資料庫中的 Reading 與 Sleep Session 輸出成 CSV 或 JSON，供電腦端分析。不含 Raw Frame。
