SELECT JSON_OBJECT(
 'id', CONCAT('pasien:', p.patient_id),
 'entitas', 'pasien',
 'judul', TRIM(CONCAT_WS(' ', pn.given_name, pn.middle_name, pn.family_name)),
 'alias', (SELECT JSON_ARRAYAGG(TRIM(CONCAT_WS(' ', pn2.given_name, pn2.family_name)))
           FROM person_name pn2 WHERE pn2.person_id=p.patient_id AND pn2.voided=0
           AND pn2.preferred=0),
 'kode', (SELECT JSON_ARRAYAGG(pi.identifier) FROM patient_identifier pi
          WHERE pi.patient_id=p.patient_id AND pi.voided=0),
 'konteks', CONCAT_WS(' ', pr.gender, YEAR(pr.birthdate))
) AS j
FROM patient p
JOIN person pr ON pr.person_id=p.patient_id
JOIN person_name pn ON pn.person_id=p.patient_id AND pn.voided=0 AND pn.preferred=1
WHERE p.voided=0;
