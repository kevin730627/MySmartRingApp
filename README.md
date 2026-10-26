# MySmartRingApp

個人用的 Android app，直接以藍牙（BLE）連線智慧戒指，取代原廠的 FixFate app，讓健康資料保存在自己的手機上、自己掌控。

專案以自行解析戒指的 BLE 協定為基礎（見 [docs/ring-protocol.md](docs/ring-protocol.md)），不依賴原廠 app 或雲端服務。

## 功能

- **心率、血氧、睡眠**：開啟 app 自動同步戒指上的資料。
- **自訂儀表板**：選擇卡片、調整順序、設定趨勢圖的時間範圍（日、週、月）。
- **詳細頁**：心率與血氧的趨勢圖與統計；睡眠的階段圖（深睡、淺睡、REM、清醒時段），並對照睡眠期間的心率與血氧。
- **資料自主**：匯出 CSV / JSON、整份資料庫備份與還原。
- **保留原始封包**：同步時的原始位元組一併存入資料庫，解析規則修正後可從原始封包重建（Rebuild）。
- 無需登入、無雲端同步，資料只存在手機本機。

不在範圍內：HRV、體溫、血壓、睡眠分數、背景同步、警示、多戒指、多使用者、匯入 FixFate 歷史資料。完整範圍與目前進度見 [docs/mvp-spec.md](docs/mvp-spec.md)。

## 對應的硬體設備

| 項目 | 說明 |
|---|---|
| 智慧戒指 | FutureLab **PulseRing**（BLE 廣播名稱為 `PulseRing`），原廠 app 為 FixFate |
| 手機 | Android 12（API 31）以上，需支援 BLE |

### 已測試的硬體

| 類別 | 型號 |
|---|---|
| 智慧戒指 | FutureLab PulseRing |
| 手機 | Google Pixel 7（Android 17） |

以上組合是作者實際用來開發與驗證的環境，同步、儀表板與睡眠圖都已在這兩個硬體上用真實資料測試過；匯出、備份還原等功能的測試範圍見 [docs/mvp-spec.md](docs/mvp-spec.md) 的「目前狀態」。其他手機或戒指型號沒有測試過。

注意：

- 本 app 只服務**一枚**戒指。
- 戒指一次只能被一個 app 綁定，使用前必須先在 FixFate 中**解除綁定**，並建議關閉 FixFate 的藍牙連線，避免搶佔。
- 其他品牌或型號的戒指不保證相容，因為協定是針對 PulseRing 側錄與解析的。

## 安裝說明

目前沒有提供預先編譯的 APK，需自行從原始碼建置。

### 需求

- JDK 17
- Android SDK（compileSdk 36），可透過 Android Studio 安裝
- 一支開啟「開發人員選項」與「USB 偵錯」的 Android 手機（或 Android 12 以上的模擬器；模擬器無法連線實體戒指，只能看介面）

### 建置與安裝

```bash
git clone https://github.com/kevin730627/MySmartRingApp.git
cd MySmartRingApp
```

用 Android Studio 開啟專案資料夾，等 Gradle 同步完成後按 Run；或用命令列把 debug 版安裝到已連線的手機：

```bash
./gradlew :app:installDebug
```

Windows 的 PowerShell 或 cmd 請改用 `gradlew.bat :app:installDebug`。

若 Gradle 找不到 Android SDK，請在專案根目錄建立 `local.properties`（此檔已列入 `.gitignore`，不會被提交）：

```properties
sdk.dir=/path/to/Android/Sdk
```

### 首次使用

1. 在 FixFate 中解除戒指的綁定。
2. 開啟手機藍牙，並把戒指放在手機附近。
3. 開啟 MySmartRingApp，依提示授予「附近裝置」（藍牙）權限。本 app 只要求 `BLUETOOTH_SCAN` 與 `BLUETOOTH_CONNECT`，不要求位置權限。
4. 第一次開啟會掃描名稱為 `PulseRing` 的裝置，選取後 app 會記住它，之後開啟 app 自動同步，儀表板下拉可重試。
5. 到設定頁可查看目前配對的戒指，重新配對或解除配對（解除配對只忘記戒指，已儲存的資料都保留）。

## 開發

專案分成兩個 Gradle 模組：

| 模組 | 內容 |
|---|---|
| `ring-protocol` | 純 Kotlin 的協定層：訊框、CRC16、各資料型別解析。可在 JVM 上單元測試 |
| `app` | Android app：Jetpack Compose 介面、Room 資料庫、BLE 連線與同步 |

技術：Kotlin、Jetpack Compose（Material 3）、Room、Kotlin Coroutines / Flow、Vico（圖表）。

執行全部單元測試（含以真實側錄檔回放的協定測試）：

```bash
./gradlew test
```

## 文件

- [docs/mvp-spec.md](docs/mvp-spec.md)：MVP 規格、行為與目前進度
- [docs/ring-protocol.md](docs/ring-protocol.md)：PulseRing BLE 協定筆記
- [docs/adr](docs/adr)：架構決策紀錄
- [GLOSSARY.md](GLOSSARY.md)：領域詞彙表

## 隱私

所有資料只存在手機本機，app 沒有網路連線與帳號系統，並關閉 Android 自動備份。需要換機或備份時，請使用設定頁的資料庫備份與還原。

`ring-protocol/src/test/resources/capture-2026-10-24.txt` 是測試用的戒指封包側錄，內含作者一晚的真實心率、血氧與睡眠原始資料。

## 授權

本專案以 [MIT License](LICENSE) 授權。

## 免責聲明

本專案為個人學習與自用專案，與 FutureLab 及 FixFate 無關。協定內容來自對自己裝置的藍牙封包側錄與對照，僅供互通性與個人使用。產出的數值不是醫療診斷，請勿作為醫療判斷依據。
