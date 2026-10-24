# MVP 規格

個人用的 Android app，取代 PulseRing 原廠的 FixFate，資料自己掌控。詞彙見 [GLOSSARY.md](../GLOSSARY.md)，決策見 [docs/adr](adr)，協定見 [ring-protocol.md](ring-protocol.md)。

## 範圍

**包含**：心率、血氧、睡眠；自訂儀表板；詳細頁；CSV/JSON 匯出；資料庫備份與還原；重建。
**不包含**：步數、HRV、體溫、血壓、睡眠分數、背景同步、警示、AI 功能、多戒指、多使用者、登入、雲端同步、自由版面編輯器、匯入 FixFate 歷史資料。

## 環境

- Pixel 7，Android 17，minSdk 31。Kotlin、Jetpack Compose。
- 兩個 Gradle 模組：`ring-protocol`（純 Kotlin，協定解析，JVM 單元測試）與 `app`。
- ViewModel 加 Flow 加 Room。手動注入，不用 Hilt。
- 圖表：折線與統計用 Vico；睡眠階段圖用 Canvas 自繪（見下）。
- 權限只有 `BLUETOOTH_SCAN`（宣告不推導位置）與 `BLUETOOTH_CONNECT`。關閉 Android 自動備份。
- 介面繁體中文，識別字英文。

## 行為

- **配對**：第一次開啟掃描名稱為 `PulseRing` 的裝置，選取後記住 MAC。只有一枚。設定頁可重新配對。戒指必須先從 FixFate 解除綁定。
- **Sync**：開啟 app 自動同步，下拉可重試。流程：校時、問筆數、收資料、確認。**Raw Frame 寫入資料庫成功後，才送清除命令。**
- **去重**：Reading 以（Metric、時間點）為唯一鍵，重複忽略。Sleep Session 以入睡時間為唯一鍵。
- **時間**：戒指本地時間，以同步當下手機時區換算成 UTC 瞬間存放。睡眠歸在醒來那天。
- **Rebuild**：清除 `reading`、`sleep_session`、`sleep_segment`，依最新解析器從 `raw_frame` 重算。必須冪等。

## 資料表

`raw_frame`、`reading`、`sleep_session`、`sleep_segment`、`sync`、`dashboard_card`、`paired_ring`。欄位以 Room 定義為準（`app/src/main/kotlin/com/mysmartring/data/Entities.kt`）。

與最初討論的差異：Reading 與 Sleep Session 記錄的是來源 `sync_id`，不是 `raw_frame_id`。同一型別的多個封包是同一條位元組串流（可能切在欄位中間），一筆 Reading 無法對應到單一個 Raw Frame。`sync` 另外記錄當時的時區，Rebuild 需要它換算戒指時間。

## 畫面（四個）

1. **儀表板**：Card 清單。
2. **儀表板設定**：選 Card、排序、時間範圍（日、週、月）。
3. **Metric 詳細頁**：點 Card 進入。
4. **設定頁**：配對、匯出、備份與還原資料庫檔、重建。

**Card 種類**：每個 Metric 有最新值、趨勢折線、統計摘要（平均、最高、最低）；睡眠另有階段分布。
**預設儀表板**：心率、血氧、睡眠各一張最新值；心率與血氧各一張趨勢（日）；睡眠一張階段分布。

**睡眠階段圖**：參考 Google Health 的睡眠圖（`captures/google_health_sleep_chart_01.jpg`）：每個階段一條橫帶，階段之間以細線連接，橫軸為時間。本戒指只有三個階段（深睡、淺睡、REM），沒有清醒帶。

## 匯出

- 每個 Metric 一個 CSV（時間、數值）。
- 睡眠另一個 CSV（入睡、醒來、各階段分鐘數）。
- JSON 一次輸出全部。
- 不輸出 Raw Frame。需要時直接備份資料庫檔。

## 尚未解決

- CRC16 演算法與初始值。
- `0518` 末段未知欄位（疑似體溫）、睡眠摘要 8 位元組。
- FixFate 淺睡 337 分鐘與封包加總 306 分鐘的 31 分鐘差異。
- `0511` 活動資料欄位意義（步數相關，MVP 外）。
- 戒指自動量測的實際間隔（樣本中約 10 分鐘），以及一次可累積多少筆。

## 建議實作順序

1. `ring-protocol`：訊框與 CRC、`0515`/`0518`/`0513` 解析器，用 `captures/btsnoop_hci.log` 回放測試。
2. Room 資料庫與 Rebuild，用上述測試資料驗證。
3. BLE 連線、配對與 Sync，真機驗證，並確認「先寫入再清除」。
4. 儀表板與 Card，先做心率最新值與趨勢。
5. 睡眠 Card 與階段圖。
6. 詳細頁、設定頁、匯出、備份與還原。
