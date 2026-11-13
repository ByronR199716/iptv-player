<div align="center">

# mundoNET

**Reproductor IPTV para celulares, tablets y Android TV.**
*mundoNET RC*

</div>

---

mundoNET reproduce tus propias listas (M3U, Xtream y más). **No incluye canales ni listas**:
cada usuario agrega sus fuentes.

## Origen y licencia

mundoNET es una versión personalizada de **[M3UAndroid](https://github.com/oxyroid/M3UAndroid)**
de *oxyroid* y colaboradores, publicada bajo la **GNU General Public License v3.0** (ver [LICENSE](LICENSE)).

Por esa licencia, mundoNET también es software libre bajo GPL-3.0: cualquiera que reciba la app
puede pedir y modificar su código fuente, que está en este repositorio. Se conservan los avisos de
autoría originales. Los cambios de esta versión: nombre, logo e iconos de mundoNET, identificadores
de paquete propios (`com.mundonet.smartphone` y `com.mundonet.tv`) y compilación automática.

## Descargar

Cada vez que se sube un cambio, GitHub compila los APK:
pestaña **Actions** → última ejecución → **Artifacts** → `mundoNET-apk`.

- `mundonet-smartphone*.apk`: celulares y tablets.
- `mundonet-tv*.apk`: Android TV / TV Box.

## Compilar

```
git clone --recurse-submodules <este repositorio>
./gradlew :app:smartphone:assembleRelease :app:tv:assembleRelease
```

Se necesita JDK 17 y el SDK de Android.
