-- Mirror of ConditionSource.java. One JSON row per conditions row (voided = 0).
-- Fields match the virtual-document contract (docs/kontrak-data.md
-- "Entitas kedelapan: kondisi"):
--   judul         = condition_coded concept name (FULLY_SPECIFIED, 'en', lowest
--                   concept_name_id); ONLY when condition_coded IS NULL fall back
--                   to condition_non_coded (demo data fills that column with
--                   placeholder text even on coded rows).
--   alias         = [patient preferred full name]
--   kode          = NULL
--   konteks       = clinical_status + onset_date  (NOT indexed)
--   tautan_pasien = conditions.patient_id
-- Rows whose coded concept has no en/FULLY_SPECIFIED name emit judul = NULL and
-- are skipped by the Python loader, matching Java's "if (title == null) continue"
-- (Java does NOT fall back to condition_non_coded when the code is present).
SELECT JSON_OBJECT(
  'id',            CONCAT('kondisi:', c.condition_id),
  'entitas',       'kondisi',
  'judul',         CASE WHEN c.condition_coded IS NOT NULL
                        THEN (SELECT cn.name FROM concept_name cn
                              WHERE cn.concept_id = c.condition_coded AND cn.voided = 0
                                AND cn.locale = 'en' AND cn.concept_name_type = 'FULLY_SPECIFIED'
                              ORDER BY cn.concept_name_id LIMIT 1)
                        ELSE c.condition_non_coded END,
  'alias',         (SELECT JSON_ARRAY(TRIM(CONCAT_WS(' ', pn.given_name, pn.middle_name, pn.family_name)))
                    FROM person_name pn
                    WHERE pn.person_id = c.patient_id AND pn.voided = 0 AND pn.preferred = 1
                    ORDER BY pn.person_name_id LIMIT 1),
  'kode',          NULL,
  'konteks',       CONCAT_WS(' ', c.clinical_status, DATE(c.onset_date)),
  'tautan_pasien', c.patient_id
) AS j
FROM conditions c
WHERE c.voided = 0
ORDER BY c.condition_id;
