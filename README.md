# LocalPlayer

Android-плеер для музыки, которая лежит на устройстве. Без интернета, аккаунтов и серверов.
Техническое задание — в файле «ТЗ Android-плеер для локальной музыки.md».

## Сборка

Нужны JDK 17+ и Android SDK с платформой 37 (Android Studio ставит их сама).

```
./gradlew assembleDebug        # debug APK: app/build/outputs/apk/debug/
./gradlew assembleRelease      # release с R8, ~3 МБ
./gradlew testDebugUnitTest    # unit-тесты
```

Release пока подписывается debug-ключом, чтобы его можно было поставить на эмулятор.
Перед публикацией замените `signingConfig` в `app/build.gradle.kts` на свой ключ.

## Стек

Kotlin 2.4, Jetpack Compose + Material 3, Navigation Compose (типобезопасные маршруты),
Media3 (ExoPlayer + MediaSessionService), Room, DataStore, Hilt, Coil 3.
Версии зафиксированы в `gradle/libs.versions.toml`. `minSdk` 26, `targetSdk`/`compileSdk` 37.

## Архитектура

MVVM с однонаправленным потоком данных: экраны читают `StateFlow` из ViewModel,
ViewModel берёт данные из репозиториев, а плеером управляет только через `MediaController`.

```
com.localplayer.app/
├── data/
│   ├── model/        Track, Album, Artist, FolderNode, Library, настройки
│   ├── mediastore/   MediaStoreSource (+ Android-реализация), LibraryBuilder, MediaStoreRepository
│   ├── db/           Room: плейлисты, «Избранное» (id = 1), счётчики прослушиваний
│   ├── prefs/        SettingsRepository, SessionRepository (DataStore)
│   └── PlaylistRepository.kt
├── playback/         PlaybackService (единственный ExoPlayer), PlayerConnection, SleepTimer
├── ui/
│   ├── actions/      меню «⋮», множественный выбор, диалоги
│   ├── components/   TrackRow, AlbumCard, MiniPlayer, EmptyState, алфавитный индекс, обложки
│   ├── navigation/   маршруты, AppScaffold (нижняя панель / NavigationRail), NavHost
│   └── screens/      library, detail, playlists, search, player, queue, settings, permission
└── di/               Hilt-модули
```

Ключевые решения:

- **ExoPlayer живёт только в `PlaybackService`.** UI подключается через `MediaController`
  пока Activity видима (`onStart`/`onStop`), поэтому музыка переживает поворот и закрытие экрана.
- **Room хранит только id треков из MediaStore.** Метаданные берутся из MediaStore;
  если файл удалён, трек в плейлисте показывается серым.
- **Фильтры библиотеки (короткие файлы, исключённые папки) применяются в памяти** —
  смена порога в настройках не требует пересканирования.
- **Обложки** грузятся своим фетчером Coil: `loadThumbnail` на Android 10+,
  встроенная картинка через `MediaMetadataRetriever` на 8–9. Для уведомления —
  свой `BitmapLoader` в сервисе.
- **Таймер сна** работает с плеером внутри сервиса, а не через UI, — чтобы сработать при выключенном экране.

## Что проверено на эмуляторах

| Проверка | Android 8 (API 26) | Android 14 (API 34) |
| --- | --- | --- |
| Запрос нужного разрешения | READ_EXTERNAL_STORAGE | READ_MEDIA_AUDIO + уведомления |
| Библиотека, вкладки, сортировка, поиск | ✓ | ✓ |
| Воспроизведение, мини- и полный плеер, foreground-уведомление | ✓ | ✓ |
| Восстановление сессии после убийства процесса | — | ✓ |
| Плейлисты, избранное, перетаскивание, множественный выбор | — | ✓ |
| Удаление файла через системный диалог | недоступно (по ТЗ — Android 11+) | ✓ |
| Таймер сна «до конца трека» | — | ✓ |
| Смена темы без перезапуска | — | ✓ |
| Альбомная ориентация (NavigationRail, плеер в две колонки) | — | ✓ |
| Release-сборка (R8) | — | ✓ |

## Известные ограничения и TODO

- Теги старых MP3 в cp1251 не перекодируются — приложение доверяет MediaStore.
- При перемешивании «Играть следующим» ставит трек в случайное место shuffle-порядка;
  после перезапуска порядок перемешивания генерируется заново.
- Список лицензий в «О приложении» ведётся вручную.
- Строки только на русском. Plurals («5 треков») корректны на устройстве с русской локалью;
  на устройстве с английской локалью Android применяет английские правила, пока нет `values-en`.
- Функции из раздела 9 ТЗ (эквалайзер, тексты, виджет, M3U и др.) не реализованы — это следующие версии.
