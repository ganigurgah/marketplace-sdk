# Contribute to Marketplace SDK

Marketplace SDK projesine katkıda bulunmak (contribute) istediğiniz için teşekkür ederiz! Açık kaynaklı bir topluluğun gücüne inanıyoruz ve bu belgede süreci herkes için olabildiğince şeffaf ve pürüzsüz hale getirmeyi amaçlıyoruz.

## 🤝 Code of Conduct (Davranış Kuralları)

Projede saygılı, yapıcı ve profesyonel bir iletişim dili beklenir. Herhangi bir toksik davranış, ayrımcılık veya nezaketsizlik kabul edilmez.

## 🚀 Yeni Pazaryeri Adapter'ı Eklemek Katkıda Bulunanlar İçin Zorunlu Kurallar

Eğer SDK hedeflerine yeni bir pazaryeri (örneğin Çiçeksepeti, PttAVM vb.) ekleyecekseniz şu kurallara uymanız zorunludur:

1. **SPI Uyumsuzluğu:** Adapter sınıfınız kesinlikle `io.marketplace.sdk.core.spi.MarketplaceAdapter` interfacesini uygulamalıdır (implements).
2. **Config-Driven Pattern:** Asla kod içine hardcode (sabit değerli) base URL'ler, credential anahtarları veya path'ler girmeyin. Tüm uç noktalar ve haritalamalar YAML dosyalarından okunmalı ve config modeliyle enjekte edilmelidir.
3. **WireMock Zorunluluğu:** `sdk-test-support` modülünde eklediğiniz API ile ilgili %100 kapsama sahip WireMock E2E Integration testini yazmazsanız Pull Request reddedilecektir. Eklediğiniz servisin güncel JSON/SOAP yük (payload) örneklerini teste ekleyin.

## 🛠️ Pull Request (PR) Süreci

Yeni bir özellik veya hata düzeltmesi eklerken:
1. Depoyu fork'layın ve `feature/sizin-ozelliginiz` veya `bugfix/hata-ismi` isminde yeni bir branch oluşturun.
2. Yazdığınız kod genel tasarım desenleri ile uyumlu olmalı. Lütfen `SKILL.md` (ve Kotlin/Java standart formatlarını) okuyun.
3. Testleri `mvn clean test` ile lokalinizde çalıştırarak hiçbir şeyin kırılmadığını teyit edin.
4. Anlaşılır bir PR başlığı ve açıklaması (gerekiyorsa ekran görüntüleri ile) ekleyin.

## 🐛 Bug Bildirimi

Hata bildirimleri yaparken lütfen:
- Karşılaşılan hatanın adımlarını,
- Hangi pazaryeri API'sini ve hangi YAML konfigürasyonunu kullandığınızı (Credential'ları maskeleyerek),
- Alınan Exception Stacktrace'i veya beklenmeyen HTTP yanıt gövdesini (JSON body),
detaylı bir şekilde GitHub Issues üzerinden belirtin.

---

*Katkılarınız bu projenin temel taşıdır. Birlikte daha güvenilir ve Enterprise standartlarına uygun bir e-ticaret altyapısı inşa ediyoruz.*
