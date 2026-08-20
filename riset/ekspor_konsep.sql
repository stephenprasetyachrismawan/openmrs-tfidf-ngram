SELECT JSON_OBJECT(
 'id', c.concept_id,
 'uuid', c.uuid,
 'kelas', cc.name,
 'tipe_data', cdt.name,
 'nama', (SELECT cn.name FROM concept_name cn WHERE cn.concept_id=c.concept_id
          AND cn.voided=0 AND cn.concept_name_type='FULLY_SPECIFIED' AND cn.locale='en' LIMIT 1),
 'pref', (SELECT JSON_ARRAYAGG(cn.name) FROM concept_name cn WHERE cn.concept_id=c.concept_id
          AND cn.voided=0 AND cn.locale_preferred=1 AND cn.locale='en'),
 'sinonim', (SELECT JSON_ARRAYAGG(cn.name) FROM concept_name cn WHERE cn.concept_id=c.concept_id
          AND cn.voided=0 AND cn.locale='en'
          AND (cn.concept_name_type IS NULL OR cn.concept_name_type<>'FULLY_SPECIFIED')),
 'nama_lain', (SELECT JSON_ARRAYAGG(cn.name) FROM concept_name cn WHERE cn.concept_id=c.concept_id
          AND cn.voided=0 AND cn.locale<>'en'),
 'kode', (SELECT JSON_ARRAYAGG(JSON_OBJECT('sumber',crs.name,'kode',crt.code,'nama',crt.name))
          FROM concept_reference_map crm
          JOIN concept_reference_term crt ON crt.concept_reference_term_id=crm.concept_reference_term_id
          JOIN concept_reference_source crs ON crs.concept_source_id=crt.concept_source_id
          WHERE crm.concept_id=c.concept_id),
 'deskripsi', (SELECT cd.description FROM concept_description cd
          WHERE cd.concept_id=c.concept_id AND cd.locale='en' LIMIT 1),
 'anggota', (SELECT JSON_ARRAYAGG(cs.concept_id) FROM concept_set cs WHERE cs.concept_set=c.concept_id),
 'jawaban', (SELECT JSON_ARRAYAGG(ca.answer_concept) FROM concept_answer ca WHERE ca.concept_id=c.concept_id),
 'induk', (SELECT JSON_ARRAYAGG(cs2.concept_set) FROM concept_set cs2 WHERE cs2.concept_id=c.concept_id),
 'n_obs', (SELECT COUNT(*) FROM obs o WHERE o.concept_id=c.concept_id AND o.voided=0)
) AS j
FROM concept c
JOIN concept_class cc ON cc.concept_class_id=c.class_id
JOIN concept_datatype cdt ON cdt.concept_datatype_id=c.datatype_id
WHERE c.retired=0;
