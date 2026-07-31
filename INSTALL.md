# Kurulum ve Kullanim Dokumani

## 1) Depoyu GitHub'a yukleme (telefondan)

- GitHub uygulamasinda veya tarayicida yeni bir depo olusturun.
- Bu klasordeki tum dosya ve alt klasorleri (yapisini bozmadan) yeni
  deponuza yukleyin. En kolay yol: GitHub web arayuzunde deponuzu acip
  "Add file > Upload files" ile bu klasorun icindekileri surukleyip
  birakmak (klasor yapisini koruyacak sekilde).
- `main` (veya `master`) dalina push edildiginde `.github/workflows/android-ci.yml`
  otomatik calisir.

## 2) APK'yi indirme

- Depo sayfasinda **Actions** sekmesine gidin.
- En son (yesil tikli) calistirmaya tiklayin.
- Sayfanin altindaki **Artifacts** bolumunden:
  - `FolderShelf-debug-apk` -> test icin, direkt yuklenebilir.
  - `FolderShelf-release-apk` -> kucultulmus/optimize edilmis surum
    (imzalama anahtari eklenmediyse gecici olarak debug anahtariyla
    imzalanir, yine de telefona yuklenebilir).
- Indirilen `.zip` dosyasini acin, icindeki `.apk` dosyasini telefonunuza
  aktarip yukleyin ("Bilinmeyen kaynaklardan yukleme" izni gerekebilir).

## 3) Kendi imzalama anahtarinizi eklemek (istege bagli)

Gercek bir yayin/release anahtariyla imzalamak icin:

1. Bir keystore dosyaniz yoksa, `keytool` ile olusturun (bir bilgisayarda
   veya Termux gibi bir Android terminal uygulamasinda):
   ```
   keytool -genkeypair -v -keystore release.keystore -alias foldershelf -keyalg RSA -keysize 2048 -validity 10000
   ```
2. Bu dosyayi base64'e cevirin:
   ```
   base64 -w0 release.keystore > release.keystore.b64
   ```
3. Deponuzda **Settings > Secrets and variables > Actions** icine su 4
   secreti ekleyin:
   - `RELEASE_KEYSTORE_BASE64` (2. adimdaki `.b64` dosyasinin icerigi)
   - `RELEASE_KEYSTORE_PASSWORD`
   - `RELEASE_KEY_ALIAS`
   - `RELEASE_KEY_PASSWORD`
4. Bir sonraki calistirmada Release APK bu anahtarla imzalanir.

## 4) Uygulamayi kullanma

1. Uygulamayi acin, **Baslat**'a dokunun.
2. "Diger uygulamalarin uzerinde goster" izni ekranina yonlendirilirsiniz;
   FolderShelf'i acin ve izni verin, geri donun.
3. (Android 13+ ise) bildirim izni istenir, izin verin.
4. Ekranin kenarinda "+" simgesi belirir. Suruklenebilir, birakinca
   kenara yapisir. Dokununca menu acilir: Dosya Sec / Klasor Sec / Son
   Eklenenler / Ayarlar / Kapat.
5. Ana ekrandaki **Yonet** butonundan eklenen tum klasor/dosyalari
   gorebilir, yeniden adlandirabilir, silebilir, bilgilerini
   goruntuleyebilir ve paylasabilirsiniz.

## 5) Android Studio'da acmak (istege bagli, bilgisayarda)

Bu depoda `gradle-wrapper.jar` (ikili dosya) bilerek bulunmuyor, cunku bu
proje bir sohbet arayuzunde olusturuldu ve ikili dosya yazilamiyor.
Android Studio'da ilk actiginizda "Gradle wrapper eksik" uyarisi
alirsaniz, bir terminalde depo kok dizininde su komutu bir kez
calistirmaniz yeterlidir (sisteminizde Gradle kuruluysa):
```
gradle wrapper --gradle-version 9.5.1
```
Ardindan Android Studio projeyi normal sekilde senkronize eder. GitHub
Actions is akisi bunu zaten otomatik yaptigi icin CI'da herhangi bir
islem yapmaniza gerek yoktur.

## Bilinen sinirlamalar

- Klasor surukle-birak ozelligi, kullandiginiz dosya yoneticisinin
  Android'in genel surukle-birak ozelligini destekleyip desteklemedigine
  baglidir (bkz. README.md). Calismazsa "Klasor Sec" / "Dosya Sec"
  kullanin.
- Bagimlilik surumleri (AndroidX/Material vb.) bu projenin hazirlandigi
  tarih itibariyla guncel surumlerdir; ileride bir surum Maven'dan
  kaldirilir/degisirse `app/build.gradle.kts` icindeki ilgili satiri
  guncel surumle degistirmeniz yeterlidir.
