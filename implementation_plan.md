# Marketplace SDK v2.0 (Enterprise Edition) Geliştirme Planı

Bu plan, `SKILL.md` belgesinde tanımlanan Config-Driven, Type-Safe, Asenkron ve Webhook destekli Türkiye Pazaryeri Entegrasyon SDK'sının sıfırdan oluşturulmasını hedefler.

## User Review Required

> [!IMPORTANT]
> Lütfen aşağıdaki aşama maddelerini inceleyin ve sıralamanın/yaklaşımın sizin için uygun olup olmadığını teyit edin. Onay vermenizin ardından **Aşama 1** ile geliştirmeye başlanacak ve eşzamanlı olarak `task.md` üzerinden ilerleme takip edilecektir.

---

## Önerilen Geliştirme Aşamaları

### Aşama 1: Proje İskeleti ve Çekirdek (Core) Yapı
SDK'nın üzerinde yükseleceği genel iskelet, modül tanımları ve temel SPI (Service Provider Interface) arayüzlerinin inşası.
- `marketplace-sdk-parent` Root `pom.xml` dosyasının oluşturulması, modüllerin ve dependency management'ın (Jackson, OkHttp, Caffeine, vb.) tanımlanması.
- `sdk-core` modülünün oluşturulması:
  - Domain modelleri (`Order`, `Product`, `MarketplaceType`, `Credentials` vb.)
  - Operasyon tanımları (`Operation`, `OperationRequest`, `OperationResponse`, `OperationRouter`)
  - İstisna (Exception) sınıfları.
  - Temel `MarketplaceClient` ve `AsyncMarketplaceClient` arayüz tanımları.
  - HTTP temel sınıfları (`HttpClient`, `HttpRequest`, `HttpResponse`, `OkHttpClientImpl`).
  - `TokenProvider` SPI tanımı.

---

### Aşama 2: Config Engine, Tip Güvenli Mapping ve Cache
Mimarinin kalbi olan YAML okuyucu, JsonPath+Jackson destekli mapper ve hafıza yönetimi.
- `sdk-config` modülünün oluşturulması.
- `YamlConfigLoader` ve `ConfigValidator` implementasyonları.
- `FieldMapper` sınıfının yazılması: JsonPath ile ham JSON'u alıp, Jackson yardımıyla `ClassCastException` yaratmayacak Type-Safe (Tip Güvenli) POJO'lara dönüştürülmesi.
- `CacheManager` sınıfının Caffeine kütüphanesi ile yapılandırılması (Kategori, marka vb. metadata önbellek işlemleri).
- `HotReloadWatcher` ile YAML dosyalarının izlenmesi ve anında bellek yenileme sisteminin yazılması.

---

### Aşama 3: Rate Limiting (Kotalama) Yönetimi
Pazaryerlerinin atacağı 429 Too Many Requests hatalarını engellemek için Thread-Blocking olmayan akıllı istek yönetimi.
- `sdk-ratelimit` modülünün oluşturulması.
- `TokenBucketRateLimiter` algoritmasının saf Java ile yazılması.
- Retry-After mantığının asenkron akışa uygun hale getirilmesi.

---

### Aşama 4: Adapter'lar ve Asenkron/Pagination Entegrasyonu
Pazaryerlerine gerçek HTTP isteklerini atacak sınıfların ve otomatik sayfalama mekanizmalarının eklenmesi.
- `sdk-adapters` modülünün oluşturulması.
- `BaseAdapter.java` içerisine RateLimiter, Cache ve TokenProvider entegrasyonu.
- `Trendyol`, `Hepsiburada`, `N11` adaptörlerinin (Basic ve API_Key auth) uyarlanması.
- `AmazonAdapter`'ın yazılması ve `AmazonTokenProvider` ile LWA OAuth2 token yenileme mantığının eklenmesi.
- `sdk-core` tarafına Stream/Iterator tabanlı **Pagination (Sayfalama)** alt yapısının yedirilmesi. Modül içinde "sayfa çek -> bitmediyse sonraki sayfayı çek" döngüsünün tasarlanması.

---

### Aşama 5: Webhook Karşılama ve Normalizasyon
Pazaryerlerinden gelen push event'leri (sipariş durumu değişimi, anlık fiyat güncellemesi vb.) karşılamak için.
- `sdk-webhook` modülünün oluşturulması.
- `WebhookEvent` standart modelinin oluşturulması.
- `WebhookServer`'ın Javalin ile yazılması ve belirli pazaryerleri için Endpoint (`POST /webhook/{marketplace}`) açması.
- `TrendyolWebhookHandler` ve `HepsiburadaWebhookHandler` yazılarak imza doğrulama (HMAC SHA-256) ve payload normalizasyonunun yapılması.
- Async dinleyicilerin tetiklenme mimarisi (`onEvent`).

---

### Aşama 6: Admin Dashboard (Görsel Arayüz)
Kullanıcıların yaml editörü üzerinden pazaryeri Endpoint'lerini test edip değiştirebilecekleri UI.
- `sdk-admin-ui` modülünün oluşturulması.
- `AdminServer` ve `AdminApiController` sınıflarıyla HTTP API yazılması.
- `resources/admin-ui/` içine HTML/JS kodu (SPA) yerleştirilerek hot-reload, diff görüntüleme fonksiyonlarının bağlanması.

---

### Aşama 7: Test Fixture'ları ve Varsayılan Yapılandırmalar
Geliştiricilerin doğrudan SDK'yı alıp çalıştırabilmesi için gereken son adımlar.
- `marketplace-configs` altına 4 pazaryeri için Enterprise standartlarındaki varsayılan `.yaml` dosyalarının yerleştirilmesi.
- `sdk-test-support` modülünün içine `WireMock` sunucularının kurulması.
- `TrendyolIntegrationTest` ve `AmazonTest` statik JSON fixture uçuşmalarının yazılması ve test süitinin tam yeşil renkte kanıtlanması.

## Açık Sorular

- Geliştirmeye başlarken proje dökümanını kaydettiğiniz root dizini `/home/gani/projects/marketplace` kullanmamız doğru mu?
- Ek modüllerin (`sdk-ratelimit`, `sdk-webhook` vb.) Maven konfigürasyonlarını yaparken tüm paketlerin varsayılan JDK sürümünü Java 17 olarak kabul ediyorum. Ek veya daha ileri bir sürüm kullanma zorunluluğumuz var mı?

## Doğrulama Planı (Verification)

### Otomatik Testler
- Her aşamadan sonra `mvn clean install` ile derlenebilirlik kontrol edilecektir.
- Aşama 7'de WireMock kullanılarak her pazar yerinin adaptörleri (GET_ORDERS, CREATE_PRODUCT, vb.) gerçek HTTP ortamı gibi davranılarak otomatik testlere sokulacaktır. Tip güvenliği burada test edilecektir.

### Manuel Doğrulama
- Admin UI Javalin üzerinden çalıştırılacak ve tarayıcı ile değişiklikler test edilecektir.
- Hot-Reload için bir `trendyol.yaml` değiştirilecek ve runtime sırasındaki davranış loglardan doğrulanacaktır.
