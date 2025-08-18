# Android 影片編輯器

一個功能完整的Android影片編輯應用程式，使用MediaCodec進行影片處理，具備Apple風格的UI設計。

## 功能特色

### 1. 影片裁剪 (Trim)
- Apple風格的影片縮圖bar
- 拖拉調整裁剪長度
- 即時預覽和播放
- 精確的時間控制

### 2. 變速處理 (Speed)
- 支援0.25x - 4x速度調整
- 音訊同步處理
- 即時預覽效果
- 多種預設速度選項

### 3. 音訊處理 (Audio)
- 去除背景聲音
- 添加背景音樂
- 音訊混合功能
- 支援多種音訊格式

### 4. 濾鏡效果 (Filter)
- 多種濾鏡選項（原圖、復古、黑白、暖色、冷色等）
- 即時預覽效果
- 可自定義濾鏡參數

### 5. 檔案管理
- 查看處理後的影片檔案
- 檔案分享功能
- 檔案刪除管理
- 檔案資訊顯示

## 技術架構

### 核心技術
- **MediaCodec**: 硬體加速影片編解碼
- **MediaExtractor**: 影片軌道提取
- **MediaMuxer**: 影片軌道合成
- **ExoPlayer**: 影片播放器

### 架構設計
- **模組化設計**: 各功能獨立，可單獨開關
- **MVVM架構**: 使用ViewModel和LiveData
- **協程**: 非同步處理
- **ViewBinding**: 安全的視圖綁定

### UI設計
- **Apple風格**: 簡潔現代的設計語言
- **Material Design 3**: 遵循最新的設計規範
- **深色主題**: 支援深色模式
- **響應式佈局**: 適配不同螢幕尺寸

## 權限處理

### Android 14 相容性
- 自動處理權限被拒絕的情況
- 使用應用程式內部儲存
- 提供檔案分享功能作為替代方案

### 權限清單
```xml
<uses-permission android:name="android.permission.READ_EXTERNAL_STORAGE" />
<uses-permission android:name="android.permission.WRITE_EXTERNAL_STORAGE" />
<uses-permission android:name="android.permission.READ_MEDIA_VIDEO" />
<uses-permission android:name="android.permission.READ_MEDIA_AUDIO" />
```

## 錯誤處理

### 常見錯誤修復
- **trackIndex is invalid**: 改善軌道索引映射處理
- **FileProvider配置**: 正確設定檔案路徑
- **權限被拒絕**: 提供內部儲存替代方案
- **詳細錯誤日誌**: 完整的錯誤診斷資訊

## 建置說明

### 環境需求
- Android Studio Arctic Fox 或更新版本
- Android SDK 24+
- Kotlin 1.9.10+

### 建置步驟
1. 克隆專案
2. 在Android Studio中開啟專案
3. 同步Gradle依賴
4. 建置並運行應用程式

### 依賴庫
```gradle
implementation 'com.google.android.exoplayer:exoplayer:2.19.1'
implementation 'com.google.android.material:material:1.11.0'
implementation 'androidx.lifecycle:lifecycle-viewmodel-ktx:2.7.0'
implementation 'org.jetbrains.kotlinx:kotlinx-coroutines-android:1.7.3'
```

## 使用說明

### 基本操作
1. 啟動應用程式
2. 選擇要編輯的影片
3. 使用底部導航切換不同功能
4. 調整參數並預覽效果
5. 點擊「完成」應用編輯
6. 在檔案管理中查看結果

### 功能模組
- **裁剪**: 拖拉調整影片長度
- **變速**: 選擇速度並預覽
- **音訊**: 選擇音訊處理選項
- **濾鏡**: 選擇並預覽濾鏡效果

## 開發注意事項

### 效能優化
- 使用MediaCodec硬體加速
- 非同步處理避免阻塞UI
- 記憶體管理最佳化
- 檔案I/O優化

### 相容性
- 支援Android 7.0 (API 24) 以上
- 處理不同裝置的硬體能力差異
- 適配不同螢幕尺寸和密度

### 測試
- 單元測試覆蓋核心邏輯
- UI測試驗證使用者流程
- 效能測試確保流暢體驗

## 授權

本專案採用 MIT 授權條款。

## 貢獻

歡迎提交Issue和Pull Request來改善專案。

## 聯絡資訊

如有問題或建議，請透過GitHub Issues聯繫。
