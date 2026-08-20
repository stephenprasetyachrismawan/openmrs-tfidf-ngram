SELECT JSON_OBJECT(
 'id', CONCAT('obat:', d.drug_id),
 'entitas', 'obat',
 'judul', d.name,
 'alias', (SELECT JSON_ARRAYAGG(cn.name) FROM concept_name cn
           WHERE cn.concept_id=d.concept_id AND cn.voided=0 AND cn.locale='en'),
 'kode', (SELECT JSON_ARRAYAGG(crt.code) FROM concept_reference_map crm
          JOIN concept_reference_term crt ON crt.concept_reference_term_id=crm.concept_reference_term_id
          WHERE crm.concept_id=d.concept_id),
 'konteks', CONCAT_WS(' ', d.strength,
            (SELECT cn3.name FROM concept_name cn3 WHERE cn3.concept_id=d.dosage_form
             AND cn3.voided=0 AND cn3.locale='en' LIMIT 1)),
 'tautan_konsep', d.concept_id
) AS j
FROM drug d
WHERE d.retired=0;
