# mundoNET (Android + Android TV)

Reproductor de IPTV para Android hecho con **Kotlin + Jetpack Compose + Media3 (ExoPlayer)**.
Funciona en celulares, tablets y Android TV / TV Box. **No incluye ningún canal ni lista**:
cada usuario agrega sus propias fuentes.

## Funciones

- Listas **M3U/M3U8** por URL o desde un archivo del dispositivo.
- Login **Xtream Codes** (servidor, usuario, contraseña) – canales en vivo.
- **Guía de programación (EPG)** XMLTV, incluyendo `.xml.gz`. Se autodetecta en Xtream y en la
  cabecera `url-tvg` de las M3U, o se puede indicar manualmente. Se asocia por `tvg-id`.
- Categorías (grupos), búsqueda, favoritos y varias listas.
- Listas grandes (decenas de miles de canales) con paginación y base de datos local (Room).
- Reproducción HLS/TS con reintento automático si la URL no tiene extensión.
- Control remoto (D-pad): ▲▼ o CH+/CH− cambian de canal, OK muestra/oculta la información,
  ► agrega/quita favorito, Atrás sale.

## Cómo compilarla

1. Instala **Android Studio** (versión reciente) con el SDK de Android 35.
2. `File > Open` y elige esta carpeta. Espera a que termine el *Gradle Sync*
   (descarga las dependencias; necesita internet).
3. Conecta un dispositivo o abre un emulador y pulsa **Run ▶**.
4. Para generar el APK: `Build > Build Bundle(s) / APK(s) > Build APK(s)`.
   Para publicar en Google Play usa `Build > Generate Signed Bundle / APK`.

> Nota: este proyecto se escribió sin poder compilarlo en el entorno donde se generó (sin acceso
> al SDK de Android). Si el primer *sync* o build muestra algún error, es lo esperado de un primer
> build: pégalo y se corrige.

## Estructura

```
app/src/main/java/com/example/iptvplayer/
├── MainActivity.kt          Tema y raíz de la app
├── data/
│   ├── Entities.kt          Tablas: listas, canales, favoritos, EPG
│   ├── Daos.kt              Consultas Room (incluye PagingSource)
│   ├── AppDatabase.kt
│   ├── Net.kt               Cliente OkHttp compartido (app + reproductor)
│   ├── Parsers.kt           Parser M3U y parser XMLTV en streaming
│   └── Repository.kt        Carga M3U / Xtream / EPG, favoritos, navegación entre canales
└── ui/
    ├── MainViewModel.kt     Estado, filtros, ExoPlayer
    ├── BrowseScreen.kt      Lista de canales, búsqueda, grupos
    ├── Dialogs.kt           Agregar / administrar listas
    └── PlayerScreen.kt      Reproductor a pantalla completa con guía
```

## Antes de publicarla

- Cambia `applicationId` y `namespace` (`com.example.iptvplayer`) en `app/build.gradle.kts`.
- No incluyas listas de canales en la app ni la promociones como forma de ver contenido de pago
  gratis: Google Play la retiraría y podrías tener responsabilidad legal. Un reproductor vacío,
  donde el usuario pone su propia fuente, es el enfoque aceptado.
- Si agregas una política de privacidad, menciona que las credenciales Xtream se guardan solo en el
  dispositivo.

## Limitaciones de la versión 1

- Solo TV en vivo (no películas ni series de Xtream/VOD).
- La guía se asocia por `tvg-id` / `epg_channel_id`; canales sin ese dato no muestran programación.
- Sin Picture-in-Picture ni reproducción en segundo plano.
- Textos de la interfaz en español, escritos directamente en el código.
