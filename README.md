# FolderShelf

Ekranin kenarina yapisan, suruklenebilir bir "+" simgesi uzerinden klasor
ve dosyalarinizi hizlica toplayip yonetmenizi ve paylasmanizi saglayan bir
Android uygulamasi. Tamamen Kotlin ile, MVVM mimarisiyle ve Android
Studio / GitHub Actions ile dogrudan derlenebilecek sekilde yazildi.

## Ozellikler

- **Ana ekran**: Tek dokunuşla izinleri isteyen ve yuzen menuyu baslatan buyuk bir "Baslat" butonu.
- **Suruklenebilir "+" simgesi**: Kenara yapisir, boyutu ayarlanabilir, konumu kalici olarak saklanir, istendiginde gizlenip tekrar gosterilebilir.
- **Acilir menu**: Dosya Sec, Klasor Sec, Son Eklenenler, Ayarlar, Kapat.
- **Klasor/Dosya ekleme**: Android'in resmi Storage Access Framework (SAF) sistem seçicisi uzerinden - klasor yapisi ve dosya isimleri hicbir zaman degistirilmez.
- **Yonetim ekrani**: Eklenen ogeleri listeler; yeniden adlandirma, silme, boyut/dosya sayisi/son degisiklik tarihi goruntuleme ve paylasma.
- **Koyu/Acik tema, Material You dinamik renk (Android 12+), tablet ve yatay mod destegi, Turkce/Ingilizce dil destegi.**

## Teknik yigin

| Katman | Teknoloji |
|---|---|
| Dil | Kotlin |
| UI | AndroidX, Material Design 3, ViewBinding |
| Mimari | MVVM (Repository -> ViewModel -> View) |
| Eszamanlilik | Kotlin Coroutines + StateFlow |
| Depolama | SharedPreferences + JSON (bkz. asagidaki not) |
| Minimum surum | Android 10 (API 29) |
| Hedef/Derleme surumu | Android 16 (API 36) |
| Derleme | AGP 9.2.0 (dahili Kotlin destegi), Gradle 9.5.1 |

### Neden Room degil de JSON tabanli depolama?

Bu proje **tamamen bir sohbet arayuzunde, yerel bir Android SDK/derleyici
olmadan** yazildi ve GitHub Actions uzerinde ilk kez derlenecek. AGP 9'un
dahili Kotlin destegi cok yeni oldugu ve Room+KSP surum eslesmesi su anda
hizla degistigi icin, test edemedigim bir ortamda ekstra bir derleyici
eklentisi eklemek gereksiz risk tasiyordu. Bunun yerine veri modeli kucuk
oldugundan basit bir SharedPreferences+JSON deposu tercih edildi.
**Mimari (Repository/ViewModel/View ayrimi) tamamen aynidir** - isterseniz
ileride `ShelfRepository` icini Room ile degistirmek, geri kalan hicbir
katmani etkilemez.

## Onemli not: Klasor surukle-birak ozelligi

Dosya yoneticinizden bir klasoru dogrudan "+" simgesinin uzerine surukleyip
birakma ozelligi eklendi (`FloatingOverlayService` icinde `OnDragListener`
ile), **ancak bu Android'in genel (cross-app) surukle-birak altyapisina ve
kullandiginiz dosya yoneticisinin bunu desteklemesine baglidir**. Butun
dosya yoneticileri bu tur bir surukleme baslatmayi desteklemez. Bu yuzden
**her zaman calisan ve garantili yontem menudeki "Klasor Sec" / "Dosya
Sec" secenekleridir** (sistemin resmi klasor/dosya secicisini acar).
Surukle-birak calismazsa uygulama size bunu bir bildirimle belirtir.

## GitHub Actions ile derleme

1. Bu depoyu (veya klasoru) kendi GitHub deponuza yukleyin (telefondan
   GitHub uygulamasi/web arayuzu ile dosyalari tek tek veya klasor
   olarak surukleyip birakabilirsiniz).
2. "Actions" sekmesine gidin; `android-ci.yml` is akisi her push'ta
   otomatik calisir (veya "Run workflow" ile manuel baslatin).
3. Is akisi bitince, ilgili calistirmanin sayfasindaki **Artifacts**
   bolumunden `FolderShelf-debug-apk` ve `FolderShelf-release-apk`
   dosyalarini indirebilirsiniz.
4. Detayli kurulum ve imzalama adimlari icin `INSTALL.md` dosyasina bakin.

## Lisans

MIT - bkz. `LICENSE` dosyasi.
