# OpenMRS Custom Development Setup for TF-IDF Semantic Query Routing

## 1. Tujuan setup

Berdasarkan proposal penelitian, sistem yang ingin dibangun adalah **modul tambahan OpenMRS** untuk:

- mengekstrak data native OpenMRS,
- membentuk dokumen pencarian per domain,
- membangun indeks TF-IDF per domain,
- melakukan query routing lintas domain,
- menyediakan REST API pencarian,
- dan menampilkan UI pencarian khusus di OpenMRS.

Artinya, arsitektur yang paling sesuai bukan mengubah core OpenMRS, tetapi membuat:

1. **backend module OpenMRS** untuk indexing, routing, retrieval, dan API;
2. **frontend O3 microfrontend** untuk search interface;
3. **server OpenMRS lokal** dengan demo data resmi untuk pengujian.

## 2. Hubungan proposal dengan arsitektur OpenMRS

OpenMRS secara umum memiliki:

- backend Java berbasis service / DAO / module,
- API layer dan REST,
- frontend modern O3 berbasis React microfrontend.

Kebutuhan proposal cocok dipetakan sebagai berikut:

- `OpenMRS Data Access` -> service backend module
- `Dataset Validator` -> service backend module
- `Document Builder` -> service backend module
- `Text Preprocessor` -> service backend module
- `TF-IDF Index Manager` -> service backend module
- `Query Router` -> service backend module
- `Domain Retriever` -> service backend module
- `Search Service` -> orchestration service backend
- `REST API` -> REST resources / controllers
- `Search Interface` -> O3 frontend app
- `Evaluation Utility` -> CLI / scheduled utility / test harness

## 3. Rekomendasi arsitektur implementasi

### Backend

Gunakan **OpenMRS module** berbasis Java sebagai komponen utama karena proposal menuntut integrasi dengan data native OpenMRS tanpa mengubah core aplikasi.

Modul ini idealnya berisi:

- `api` untuk service, model, index manager, validator
- `omod` untuk web layer / REST exposure / admin pages bila diperlukan

### Frontend

Untuk antarmuka pencarian, gunakan **O3 frontend module** terpisah jika Anda ingin UI yang modern dan konsisten dengan OpenMRS 3.x.

Jika ingin iterasi lebih cepat, fase awal bisa dimulai dengan:

- backend module dulu,
- REST API dulu,
- pengujian via Postman / curl,
- lalu sambungkan ke O3 frontend setelah logic retrieval stabil.

### Storage indeks

Sesuai proposal, metadata dokumen tetap di database, sedangkan vocabulary, IDF, dan sparse matrix bisa disimpan dalam file indeks atau cache aplikasi.

Rekomendasi praktis:

- simpan metadata dokumen + status build indeks di tabel modul,
- simpan artefak indeks TF-IDF di folder data server OpenMRS,
- gunakan versi indeks (`index_version`) agar rebuild dapat dilacak.

## 4. Setup environment penuh

### Status instalasi lokal

Environment O3 sudah dipasang dan diverifikasi di Windows. Runtime aktif dari
`openmrs-distro-referenceapplication` dengan empat service Docker: MariaDB,
backend, frontend, dan gateway. Port 80 host sudah dipakai Apache di WSL,
sehingga konfigurasi lokal memakai `http://localhost:8081`.

Maven dipasang secara portable di:

```text
C:\src\tfidf-openmrs\tools\apache-maven-3.9.16\bin\mvn.cmd
```

Image backend lokal diturunkan dari image resmi O3 dan hanya menghapus
`openconceptlab` serta `referencedemodata` module dari startup. Ini mencegah
import terminologi dan generator demo berulang menahan startup; 50 pasien dan
910 encounter demo yang sudah terbentuk tetap berada di database untuk smoke
test awal. Dataset demo yang lebih besar akan kita buat secara eksplisit pada
fase dataset proposal.

Perintah startup aktual:

```powershell
cd C:\src\tfidf-openmrs\openmrs-distro-referenceapplication
docker compose -f docker-compose.yml -f docker-compose.local.yml build backend
docker compose -f docker-compose.yml -f docker-compose.local.yml up -d
```

Verifikasi:

```powershell
powershell -ExecutionPolicy Bypass -File C:\src\tfidf-openmrs\scripts\verify-openmrs.ps1 -Port 8081
```

URL development:

- O3: `http://localhost:8081/openmrs/spa/home`
- Legacy UI: `http://localhost:8081/openmrs/`
- REST: `http://localhost:8081/openmrs/ws/rest/v1/session`

### A. Tools dasar

Install:

- Java JDK 17
- Maven 3.8+
- Git
- MySQL 8 atau MariaDB yang kompatibel
- Node.js 20+ dan npm/pnpm

Catatan:

- Dokumentasi SDK OpenMRS masih menunjukkan contoh lama di beberapa bagian, tetapi alur umumnya tetap: pakai Maven + OpenMRS SDK untuk membuat dan menjalankan server lokal.
- Hindari path Windows yang mengandung spasi untuk proyek, `JAVA_HOME`, dan home Maven bila memungkinkan.

### B. Install OpenMRS SDK

```powershell
mvn org.openmrs.maven.plugins:openmrs-sdk-maven-plugin:setup-sdk
mvn openmrs-sdk:help
```

### C. Buat server OpenMRS lokal untuk development

Paling aman untuk kebutuhan proposal adalah **Reference Application / O3 distro** yang mendukung demo data dan REST.

Wizard interaktif:

```powershell
mvn openmrs-sdk:setup
```

Saat wizard berjalan:

1. pilih setup distribution,
2. pilih Reference Application atau distro O3 yang aktif,
3. gunakan MySQL,
4. simpan `serverId`, misalnya `openmrs-tfidf`.

### D. Jalankan server

```powershell
mvn openmrs-sdk:run -DserverId=openmrs-tfidf
```

### E. Aktifkan demo data resmi

Masuk ke OpenMRS lalu set global property:

- `referencedemodata.createDemoPatientsOnNextStartup`

Tahap kerja yang selaras dengan proposal:

1. isi `100` atau `200` dulu untuk verifikasi awal,
2. restart server,
3. verifikasi relasi dan atribut,
4. baru naikkan ke `5000` untuk eksperimen final.

## 5. Struktur repo yang direkomendasikan

Karena repo saat ini masih minimal, struktur kerja yang cocok adalah:

```text
tfidf-openmrs/
  docs/
  backend/
    openmrs-module-tfidf-search/
      api/
      omod/
  frontend/
    openmrs-esm-tfidf-search-app/
  scripts/
    dev/
    eval/
  data/
    queries/
    ground-truth/
  artifacts/
    indexes/
    reports/
```

## 6. Backend module yang perlu dibuat

### A. Generate module skeleton

```powershell
mvn openmrs-sdk:create-project
```

Pilih:

- `Reference Application module` jika targetnya instance distro/refapp
- atau `Platform module` jika ingin backend-only

Untuk kasus proposal ini, saya sarankan mulai dari:

- `Reference Application module`

Nama contoh:

- module id: `tfidfsearch`
- artifact id: `tfidfsearch`
- group id: `org.openmrs.module`

### B. Paket internal yang direkomendasikan

```text
org.openmrs.module.tfidfsearch
  api/
    TfidfSearchService.java
    DatasetValidationService.java
    IndexBuildService.java
    QueryRoutingService.java
    DomainRetrievalService.java
  api/impl/
  api/model/
  api/index/
  api/preprocess/
  api/builder/
  api/eval/
  web/controller/
  web/resource/
```

### C. Enam domain sesuai proposal

Backend harus memodelkan minimal domain ini:

- `patient`
- `encounter`
- `diagnosis`
- `medication`
- `laboratory`
- `procedure`

### D. Entity extraction yang perlu disiapkan

Sesuai proposal, sumber data utama di OpenMRS:

- `Patient`, `Person`, `PersonName`, `PersonAddress`
- `Visit`, `Encounter`, `EncounterType`
- `Observation`
- `Order`, `DrugOrder`, `Drug`
- `Provider`
- `Location`
- `Concept`

### E. Komponen logic utama

Implement service berikut:

1. `DatasetValidationService`
   - hitung jumlah entitas,
   - cek relasi patient-encounter-observation-order,
   - tandai record yang tidak valid.

2. `DocumentBuilder`
   - ubah setiap record menjadi dokumen tekstual per domain,
   - simpan `document_id`, `entity_type`, `entity_uuid`, `raw_content`, `field_content`, `metadata`.

3. `TextPreprocessor`
   - lowercasing,
   - character normalization,
   - tokenization,
   - stopword removal yang aman untuk istilah klinis,
   - unigram + bigram,
   - synonym expansion terbatas.

4. `TfidfIndexManager`
   - fit vectorizer per domain,
   - simpan vocabulary,
   - simpan nilai IDF,
   - simpan sparse matrix / serialized representation,
   - support full rebuild per domain.

5. `QueryRoutingService`
   - bentuk profil domain,
   - hitung cosine similarity query vs profil domain,
   - pilih domain menggunakan threshold `tau`,
   - fallback ke top domain atau all domains.

6. `DomainRetrievalService`
   - hitung similarity query vs dokumen domain,
   - terapkan field weighting untuk domain-specific search,
   - kembalikan ranked results.

7. `TfidfSearchService`
   - general search orchestration,
   - domain-specific search orchestration,
   - gabungkan hasil + metadata + explainability sederhana.

## 7. REST API yang perlu disiapkan

Minimal siapkan endpoint:

```text
POST /ws/rest/v1/tfidfsearch/search
POST /ws/rest/v1/tfidfsearch/index/rebuild
POST /ws/rest/v1/tfidfsearch/index/rebuild/{domain}
GET  /ws/rest/v1/tfidfsearch/index/status
GET  /ws/rest/v1/tfidfsearch/dataset/profile
```

Kontrak request/response sebaiknya mengikuti proposal:

### General search

```json
{
  "query": "diabetes patient taking metformin",
  "mode": "general",
  "limit": 10,
  "filters": {}
}
```

### Domain-specific search

```json
{
  "query": "metformin 500 mg tablet",
  "mode": "specific",
  "domain": "medication",
  "limit": 10,
  "filters": {}
}
```

## 8. Frontend O3 yang disarankan

Buat frontend terpisah bila Anda ingin search box native di O3:

```text
frontend/openmrs-esm-tfidf-search-app
```

Isi minimum:

- search page
- domain selector
- result list per domain
- filter panel
- link ke record asli OpenMRS via `entityUuid`

Komponen React minimum:

- `search-page.component.tsx`
- `search-form.component.tsx`
- `results-list.component.tsx`
- `domain-pill.component.tsx`
- `search-api.ts`

Flow frontend:

1. user mengetik query,
2. frontend memanggil REST backend,
3. tampilkan `selectedDomains`,
4. render grouped results,
5. klik hasil membuka record asli OpenMRS.

## 9. Workflow development yang paling aman

Urutan kerja yang saya sarankan:

1. setup server OpenMRS lokal,
2. aktifkan demo data kecil,
3. buat backend module skeleton,
4. implement dataset validator,
5. implement document builder untuk 1 domain dulu (`patient`),
6. implement TF-IDF indexing 1 domain,
7. implement REST endpoint pencarian sederhana,
8. tambah domain lain satu per satu,
9. implement query routing,
10. tambah frontend O3,
11. jalankan evaluasi.

Alasan urutan ini:

- proposal menuntut reproduktifitas,
- data OpenMRS cukup heterogen,
- jika langsung membangun semua domain sekaligus, debugging relasi akan sulit.

## 10. Rencana implementasi bertahap

### Fase 1

- OpenMRS server jalan
- demo data 100-200 pasien
- backend module terpasang
- patient search berbasis TF-IDF

### Fase 2

- tambah encounter, diagnosis, medication
- simpan indeks per domain
- admin trigger rebuild

### Fase 3

- tambah laboratory dan procedure
- general search + query routing
- threshold experiment

### Fase 4

- O3 frontend
- evaluasi precision / recall / MRR / nDCG / response time

## 11. Hal yang perlu dijaga agar tetap sesuai proposal

- jangan ubah core OpenMRS
- gunakan demo data resmi, bukan dataset pasien nyata
- simpan UUID sumber pada setiap dokumen indeks
- catat versi OpenMRS, versi module, snapshot data, dan parameter preprocessing
- lakukan full rebuild bila sinonim, preprocessing, atau struktur dokumen berubah
- prioritaskan manual rebuild, incremental update bisa ditunda

## 12. Setup minimum yang saya rekomendasikan untuk mulai sekarang

Kalau target Anda adalah mulai develop secepat mungkin, setup paling realistis adalah:

1. OpenMRS SDK
2. satu server `openmrs-tfidf`
3. MySQL lokal
4. demo data 100 pasien
5. satu backend module `tfidfsearch`
6. satu endpoint `/search`
7. satu domain awal: `patient`

Setelah itu baru diperluas ke lima domain lain.

## 13. Keputusan teknis yang saya rekomendasikan

- **Arsitektur utama**: backend module + optional O3 frontend
- **Data source**: native OpenMRS services / model
- **Indexing awal**: full rebuild manual
- **Frontend awal**: Postman / curl dulu, UI belakangan
- **Domain implementasi pertama**: `patient`
- **DB**: MySQL
- **Evaluasi**: query set dan ground truth dipisah dari kode modul

## 14. Langkah berikutnya yang paling masuk akal

Setelah panduan ini, langkah implementasi yang paling tepat adalah:

1. scaffold backend module `tfidfsearch`,
2. siapkan server OpenMRS lokal via SDK,
3. buat struktur package sesuai komponen proposal,
4. implement `DatasetValidationService` dan `PatientDocumentBuilder` terlebih dulu.
