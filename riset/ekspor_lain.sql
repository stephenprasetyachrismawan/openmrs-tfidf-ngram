SELECT JSON_OBJECT('id', CONCAT('form:', f.form_id), 'entitas','form',
 'judul', f.name, 'alias', NULL, 'kode', f.version,
 'konteks', CONCAT_WS(' ', f.description,
   (SELECT et.name FROM encounter_type et WHERE et.encounter_type_id=f.encounter_type))) AS j
FROM form f WHERE f.retired=0
UNION ALL
SELECT JSON_OBJECT('id', CONCAT('lokasi:', l.location_id), 'entitas','lokasi',
 'judul', l.name,
 'alias', (SELECT JSON_ARRAYAGG(lt.name) FROM location_tag_map ltm
           JOIN location_tag lt ON lt.location_tag_id=ltm.location_tag_id
           WHERE ltm.location_id=l.location_id),
 'kode', NULL,
 'konteks', CONCAT_WS(' ', l.description, l.city_village, l.state_province)) AS j
FROM location l WHERE l.retired=0
UNION ALL
SELECT JSON_OBJECT('id', CONCAT('provider:', pv.provider_id), 'entitas','provider',
 'judul', COALESCE(pv.name, TRIM(CONCAT_WS(' ', pn.given_name, pn.family_name))),
 'alias', NULL, 'kode', pv.identifier, 'konteks', pv.uuid) AS j
FROM provider pv
LEFT JOIN person_name pn ON pn.person_id=pv.person_id AND pn.voided=0 AND pn.preferred=1
WHERE pv.retired=0;
