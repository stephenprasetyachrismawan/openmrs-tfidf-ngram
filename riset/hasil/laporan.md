# Hasil Eksperimen TF-IDF pada Demo Data OpenMRS

Korpus: **4249 konsep** dari database MariaDB OpenMRS (distro referenceapplication, demo data resmi).  
Sinonim: 3624 · Mapping terminologi: 18726  
Waktu indeks: 1.28 s · Query: 100 dev (tuning) / 200 test (dilaporkan)

Bobot field hasil tuning di dev: `{'nama': 1.0, 'sinonim': 1.0, 'kode': 1.0, 'desk': 0.25}`

## Tabel utama (split test)

| Sistem | P@1 | P@5 | P@10 | R@10 | MRR | MAP | nDCG@10 | latensi ms | query 0-hasil |
|---|---|---|---|---|---|---|---|---|---|
| B0  OpenMRS heuristik | 0.840 | 0.190 | 0.095 | 0.634 | 0.848 | 0.624 | 0.750 | 0.7 | 14% |
| B1  TF-IDF VSM | 0.765 | 0.244 | 0.131 | 0.727 | 0.805 | 0.656 | 0.747 | 0.1 | 8% |
| B2  BM25 | 0.805 | 0.244 | 0.134 | 0.733 | 0.828 | 0.678 | 0.763 | 0.1 | 8% |
| E1  TF-IDF + QE | 0.770 | 0.245 | 0.134 | 0.730 | 0.808 | 0.664 | 0.750 | 0.2 | 8% |
| E2  TF-IDF field-weighted | 0.770 | 0.238 | 0.134 | 0.733 | 0.815 | 0.657 | 0.756 | 0.1 | 8% |
| E3  field-weighted + QE | 0.770 | 0.239 | 0.135 | 0.735 | 0.814 | 0.659 | 0.756 | 0.3 | 8% |
| E4  E3 + char 4-gram | 0.870 | 0.280 | 0.158 | 0.847 | 0.925 | 0.751 | 0.861 | 2.0 | 0% |

## nDCG@10 per jenis degradasi query

| Sistem | persis | typo | trunkasi | hilang_kata | urut_balik |
|---|---|---|---|---|---|
| B0  OpenMRS heuristik | 0.858 | 0.000 | 0.910 | 0.843 | 0.890 |
| B1  TF-IDF VSM | 0.839 | 0.773 | 0.390 | 0.861 | 0.921 |
| B2  BM25 | 0.875 | 0.811 | 0.378 | 0.869 | 0.941 |
| E1  TF-IDF + QE | 0.904 | 0.750 | 0.390 | 0.825 | 0.921 |
| E2  TF-IDF field-weighted | 0.884 | 0.828 | 0.356 | 0.852 | 0.928 |
| E3  field-weighted + QE | 0.909 | 0.797 | 0.364 | 0.841 | 0.928 |
| E4  E3 + char 4-gram | 0.900 | 0.849 | 0.757 | 0.857 | 0.946 |

## Uji signifikansi (paired bootstrap 5.000x, selisih nDCG@10)

| Sistem | vs B0 (OpenMRS) | CI95 | p | vs B1 (TF-IDF) | CI95 | p |
|---|---|---|---|---|---|---|
| B1  TF-IDF VSM | -0.003 | [-0.068, +0.061] | 0.927 | +0.000 | [+0.000, +0.000] | 1.000 |
| B2  BM25 | +0.013 | [-0.051, +0.078] | 0.706 | +0.016 | [+0.002, +0.030] | 0.027 |
| E1  TF-IDF + QE | -0.000 | [-0.063, +0.065] | 0.997 | +0.003 | [-0.013, +0.017] | 0.706 |
| E2  TF-IDF field-weighted | +0.006 | [-0.059, +0.071] | 0.874 | +0.009 | [-0.012, +0.028] | 0.399 |
| E3  field-weighted + QE | +0.006 | [-0.058, +0.070] | 0.854 | +0.009 | [-0.013, +0.030] | 0.405 |
| E4  E3 + char 4-gram | +0.111 | [+0.063, +0.161] | 0.000 | +0.114 | [+0.080, +0.151] | 0.000 |

## Kasus yang didokumentasikan komunitas OpenMRS

| Query | Catatan | B0-OpenMRS | B1-TFIDF | E3-QE+field | E4-+ngram |
|---|---|---|---|---|---|
| `acetaminophen` | exact name harus di peringkat 1 | Acetaminophen ; Acetaminophen / codeine ; Tramadol & Acetaminophen | Tramadol & Acetaminophen ; Acetaminophen,Pseudo-ephedrine,Dextrom ; Acetaminophen / oxycodone | Acetaminophen ; Sore throat or pain with swallowing ; Acetaminophen / codeine | Acetaminophen ; Tramadol & Acetaminophen ; Acetaminophen,Pseudo-ephedrine,Dextrom |
| `type 2 diabetes` | sinonim persis terkubur di peringkat 17 | Diabetes mellitus, type 2 ; Erectile dysfunction associated with t | Type 1 diabetes (E10) ; Diabetes mellitus, type 2 ; Diabetes mellitus, type 1 | Diabetes mellitus, type 2 ; Diabetes mellitus, type 1 ; Diabetes mellitus | Diabetes mellitus, type 2 ; Type 1 diabetes (E10) ; Diabetes mellitus, type 1 |
| `pulm edem` | partial token -> 0 hasil di OpenMRS | Pulmonary edema ; Acute pulmonary edema ; Postoperative pulmonary edema | (0 hasil) | (0 hasil) | Pulmonary edema ; Acute pulmonary edema ; Postoperative pulmonary edema |
| `aspirin` | tertarik ke acetaminophen via sinonim "aspirin free" | Acetylsalicylate sodium ; Acetaminophen | Acetylsalicylate sodium ; Acetaminophen | Acetylsalicylate sodium ; Low ; Acetaminophen | Acetylsalicylate sodium ; Acetaminophen ; Low |
| `malaria` | kontrol | Malaria ; Rapid test for malaria ; H/O: Malaria | Rapid test for malaria ; H/O: Malaria ; Positive for Plasmodium falciparum | Malaria ; Rapid test for malaria ; Cerebral malaria | Malaria ; Rapid test for malaria ; Quartan malaria |
| `hypertension` | kontrol | Hypertension ; Portal hypertension ; Essential hypertension | Pregnancy-induced hypertension ; History of hypertension ; Family history of hypertension | Hypertension ; Pregnancy-induced hypertension ; History of hypertension | Hypertension ; Pregnancy-induced hypertension ; History of hypertension |
| `tuberculosis` | kontrol | Tuberculosis ; Mycobacterium tuberculosis ; Bone tuberculosis | Mycobacterium tuberculosis ; Mycobacterium tuberculosis complex ; results, tuberculosis culture | Tuberculosis ; Mycobacterium tuberculosis ; Disseminated tuberculosis | Tuberculosis ; Mycobacterium tuberculosis ; Mycobacterium tuberculosis complex |
| `diabete melitus` | typo | (0 hasil) | (0 hasil) | (0 hasil) | Diabetes mellitus, type 2 ; Drug-Induced Diabetes Mellitus ; History of diabetes mellitus |
| `preg test` | trunkasi | Urine pregnancy test ; Serum pregnancy test, qualitative | Mother pregnant or currently breastfee ; Test Results ; Test Done | HIV Test ; Test Results ; Test Done | Test Results ; Test Done ; HIV Test |
| `fever headache` | multi-gejala | (0 hasil) | Viral haemorrhagic fever ; Typhoid fever ; Acute rheumatic fever (I00) | Headache ; Typhoid fever ; Severe headache | Typhoid fever ; Headache ; Viral haemorrhagic fever |