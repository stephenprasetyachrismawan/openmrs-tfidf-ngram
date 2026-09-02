-- Mirror of HasilLabSource.java. One JSON row per obs whose concept class is
-- Test or LabSet (voided = 0). Fields match the virtual-document contract
-- (docs/kontrak-data.md "Entitas ketujuh: hasillab"):
--   judul         = test concept name (FULLY_SPECIFIED, locale 'en', lowest concept_name_id)
--   alias         = [patient preferred full name]  (dropped downstream if null)
--   kode          = NULL
--   konteks       = value + date  (NOT indexed -- format is cosmetic)
--   tautan_pasien = obs.person_id
-- Rows whose concept has no en/FULLY_SPECIFIED name emit judul = NULL and are
-- skipped by the Python loader (eksperimen_k2.muat8), matching Java's
-- "if (title == null) continue".
SELECT JSON_OBJECT(
  'id',            CONCAT('hasillab:', o.obs_id),
  'entitas',       'hasillab',
  'judul',         (SELECT cn.name FROM concept_name cn
                    WHERE cn.concept_id = o.concept_id AND cn.voided = 0
                      AND cn.locale = 'en' AND cn.concept_name_type = 'FULLY_SPECIFIED'
                    ORDER BY cn.concept_name_id LIMIT 1),
  'alias',         (SELECT JSON_ARRAY(TRIM(CONCAT_WS(' ', pn.given_name, pn.middle_name, pn.family_name)))
                    FROM person_name pn
                    WHERE pn.person_id = o.person_id AND pn.voided = 0 AND pn.preferred = 1
                    ORDER BY pn.person_name_id LIMIT 1),
  'kode',          NULL,
  'konteks',       CONCAT_WS(' ',
                     COALESCE(CAST(o.value_numeric AS CHAR), o.value_text, ''),
                     DATE(o.obs_datetime)),
  'tautan_pasien', o.person_id
) AS j
FROM obs o
JOIN concept c        ON c.concept_id = o.concept_id
JOIN concept_class cc ON cc.concept_class_id = c.class_id
WHERE o.voided = 0
  AND cc.name IN ('Test', 'LabSet')
ORDER BY o.obs_id;
