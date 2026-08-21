<%@ include file="/WEB-INF/template/include.jsp" %>
<%@ include file="/WEB-INF/template/header.jsp" %>

<h2><spring:message code="unifiedsearch.page.heading"/></h2>

<style>
	#us-app { max-width: 900px; }
	#us-controls { display: flex; gap: 8px; align-items: center; margin-bottom: 4px; }
	#us-query { flex: 1; padding: 6px 8px; font-size: 1.05em; }
	#us-mode { padding: 6px; }
	#us-mode-desc { color: #555; font-size: 0.9em; margin: 2px 0 12px 0; }
	#us-status { min-height: 1.4em; color: #555; margin-bottom: 8px; }
	#us-status.us-error { color: #a00; }
	.us-grup { margin-bottom: 14px; }
	.us-grup h4 { margin: 0 0 4px 0; background: #eee; padding: 4px 8px; }
	.us-hit { padding: 6px 8px; border-bottom: 1px solid #eee; }
	.us-hit a { font-weight: bold; }
	.us-hit .us-skor { color: #666; font-size: 0.85em; margin-left: 6px; }
	.us-hit .us-konteks { color: #444; font-size: 0.9em; }
	.us-sorot { background: #ffef8a; }
	#eval-panel { margin-top: 28px; padding-top: 12px; border-top: 2px solid #ccc; }
	#eval-table { border-collapse: collapse; margin-top: 10px; }
	#eval-table th, #eval-table td { border: 1px solid #ccc; padding: 4px 8px; text-align: right; }
	#eval-table th:first-child, #eval-table td:first-child { text-align: left; }
	details.us-diagnostik { margin-top: 28px; }
</style>

<div id="us-app">
	<div id="us-controls">
		<input type="text" id="us-query" placeholder="Ketik kata kunci, mis. diabete melitus" autocomplete="off"/>
		<select id="us-mode">
			<option value="b0">b0 &mdash; heuristik OpenMRS (dasar, tanpa toleransi ejaan)</option>
			<option value="b1">b1 &mdash; TF-IDF kata saja</option>
			<option value="e1">e1 &mdash; TF-IDF kata + kepingan karakter (tahan salah ketik)</option>
			<option value="e3" selected="selected">e3 &mdash; e1 + Weighted RRF antar-entitas (default, diusulkan)</option>
		</select>
	</div>
	<p id="us-mode-desc" class="us-mode-desc"></p>
	<div id="us-status">Ketik untuk mencari.</div>
	<div id="us-results"></div>
</div>

<div id="eval-panel">
	<h3>Panel evaluasi</h3>
	<p>
		Menjalankan 100 query data uji pengembangan (dev, terpisah dari 180 query
		pelaporan &mdash; CLAUDE.md aturan 10) terhadap indeks yang sedang berjalan
		di server ini sekarang.
	</p>
	<div id="eval-controls">
		<select id="eval-mode">
			<option value="b0">b0 &mdash; heuristik OpenMRS</option>
			<option value="b1">b1 &mdash; TF-IDF kata saja</option>
			<option value="e1">e1 &mdash; TF-IDF kata + kepingan karakter</option>
			<option value="e3" selected="selected">e3 &mdash; e1 + Weighted RRF</option>
		</select>
		<button type="button" id="eval-run">Jalankan seluruh data uji</button>
	</div>
	<div id="eval-status"></div>
	<table id="eval-table" style="display:none">
		<thead>
			<tr>
				<th>Mode</th><th>P@1</th><th>P@5</th><th>R@10</th><th>MRR</th><th>MAP</th>
				<th>nDCG@10</th><th>% nol-hasil</th><th>Waktu (ms)</th>
			</tr>
		</thead>
		<tbody id="eval-tbody"></tbody>
	</table>
</div>

<details class="us-diagnostik">
	<summary>Diagnostik teknis (K1&ndash;K6, dihitung ulang setiap muat halaman)</summary>

	<table cellpadding="4" cellspacing="0" border="1">
		<tr><th>Entitas</th><th>Dokumen</th><th>Surface form</th></tr>
		<c:forEach var="baris" items="${dokumenPerEntitas}">
			<tr>
				<td>${baris.key}</td>
				<td align="right">${baris.value}</td>
				<td align="right">${formPerEntitas[baris.key]}</td>
			</tr>
		</c:forEach>
		<tr>
			<th>Total</th>
			<th align="right"><span id="totalDokumen">${totalDokumen}</span></th>
			<th align="right"><span id="totalForm">${totalForm}</span></th>
		</tr>
	</table>

	<p id="contoh">
		<c:forEach var="c" items="${contoh}">${c}<br/></c:forEach>
	</p>

	<h4>Indeks kepingan karakter (K4)</h4>
	<p>
		Waktu bangun (6 indeks, seluruh korpus): <span id="durasiBangunKepinganMs">${durasiBangunKepinganMs}</span> ms.
		Total kosakata kepingan: <span id="totalKepinganVocab">${totalKepinganVocab}</span>.
	</p>
	<p id="contohKepingan">
		<c:forEach var="c" items="${contohKepingan}">${c}<br/></c:forEach>
	</p>

	<h4>Fusi kata + kepingan (K5) &mdash; query "panadol"</h4>
	<p id="contohFusi">
		<c:forEach var="c" items="${contohFusi}">${c}<br/></c:forEach>
	</p>

	<h4>Indeks global dan Weighted RRF (K6) &mdash; query "diabete"</h4>
	<p id="contohRrf">
		<c:forEach var="c" items="${contohRrf}">${c}<br/></c:forEach>
	</p>
</details>

<script>
(function () {
	'use strict';
	var CTX = '${pageContext.request.contextPath}';
	var SEARCH_URL = CTX + '/ws/rest/v1/unifiedsearch';
	var EVAL_URL = CTX + '/ws/rest/v1/unifiedsearch/eval';

	var MODE_DESC = {
		b0: 'Aturan heuristik OpenMRS bawaan: cocok jika awalan kata sama persis. Tidak tahan salah ketik.',
		b1: 'TF-IDF atas kata utuh saja, tanpa kepingan karakter. Basis pembanding penelitian.',
		e1: 'TF-IDF kata digabung kepingan karakter 4-huruf (K5) &mdash; komponen yang terbukti signifikan (+0,174 nDCG@10, p<0,001).',
		e3: 'e1 ditambah Weighted RRF (K6) untuk menggabungkan enam entitas. Perbaikan kecil dibanding e1 (+0,013 nDCG@10, p=0,039) &mdash; jangan dibaca setara K4.'
	};

	var queryInput = document.getElementById('us-query');
	var modeSelect = document.getElementById('us-mode');
	var modeDesc = document.getElementById('us-mode-desc');
	var statusEl = document.getElementById('us-status');
	var resultsEl = document.getElementById('us-results');
	var debounceTimer = null;
	var requestSeq = 0;

	function escapeHtml(s) {
		return String(s).replace(/[&<>"']/g, function (c) {
			return { '&': '&amp;', '<': '&lt;', '>': '&gt;', '"': '&quot;', "'": '&#39;' }[c];
		});
	}

	/** Sorot kemunculan tiap kata query (>=2 huruf) di dalam judul, tanpa peduli huruf besar/kecil. */
	function sorot(judul, query) {
		var kata = query.split(/\s+/).filter(function (w) { return w.length >= 2; });
		var teks = escapeHtml(judul);
		if (kata.length === 0) {
			return teks;
		}
		var pola = kata.map(function (w) {
			return w.replace(/[.*+?^{}()|[\]\\$]/g, '\\$&');
		}).join('|');
		var re = new RegExp('(' + pola + ')', 'ig');
		return teks.replace(re, '<span class="us-sorot">$1</span>');
	}

	function renderModeDesc() {
		modeDesc.innerHTML = MODE_DESC[modeSelect.value] || '';
	}

	function renderResults(data) {
		var hits = data.results || [];
		if (hits.length === 0) {
			statusEl.className = '';
			statusEl.textContent = 'Tidak ada hasil untuk "' + data.query + '".';
			resultsEl.innerHTML = '';
			return;
		}
		statusEl.className = '';
		statusEl.textContent = hits.length + ' hasil untuk "' + data.query + '" (mode ' + data.mode + ').';

		var perEntitas = {};
		var urutan = [];
		hits.forEach(function (h) {
			if (!perEntitas[h.entitas]) {
				perEntitas[h.entitas] = [];
				urutan.push(h.entitas);
			}
			perEntitas[h.entitas].push(h);
		});

		var html = '';
		urutan.forEach(function (ent) {
			var list = perEntitas[ent];
			html += '<div class="us-grup"><h4>' + escapeHtml(ent) + ' (' + list.length + ')</h4>';
			list.forEach(function (h) {
				html += '<div class="us-hit">';
				html += '<a href="' + escapeHtml(h.url || '#') + '">' + sorot(h.judul, data.query) + '</a>';
				html += '<span class="us-skor">skor=' + h.skor + '</span>';
				if (h.konteks) {
					html += '<div class="us-konteks">' + escapeHtml(h.konteks) + '</div>';
				}
				html += '</div>';
			});
			html += '</div>';
		});
		resultsEl.innerHTML = html;
	}

	function jalankanPencarian() {
		var q = queryInput.value.trim();
		if (q.length === 0) {
			statusEl.className = '';
			statusEl.textContent = 'Ketik untuk mencari.';
			resultsEl.innerHTML = '';
			return;
		}
		var seq = ++requestSeq;
		statusEl.className = '';
		statusEl.textContent = 'Mencari ...';
		var url = SEARCH_URL + '?q=' + encodeURIComponent(q) + '&mode=' + encodeURIComponent(modeSelect.value) + '&limit=20';
		fetch(url, { credentials: 'same-origin' })
			.then(function (resp) {
				if (seq !== requestSeq) { return null; }
				if (!resp.ok) {
					return resp.json().catch(function () { return {}; }).then(function (body) {
						throw new Error(body.error || ('HTTP ' + resp.status));
					});
				}
				return resp.json();
			})
			.then(function (data) {
				if (data === null || seq !== requestSeq) { return; }
				renderResults(data);
			})
			.catch(function (err) {
				if (seq !== requestSeq) { return; }
				statusEl.className = 'us-error';
				statusEl.textContent = 'Galat: ' + err.message;
				resultsEl.innerHTML = '';
			});
	}

	queryInput.addEventListener('input', function () {
		window.clearTimeout(debounceTimer);
		debounceTimer = window.setTimeout(jalankanPencarian, 150);
	});
	modeSelect.addEventListener('change', function () {
		renderModeDesc();
		jalankanPencarian();
	});
	renderModeDesc();

	var evalMode = document.getElementById('eval-mode');
	var evalRun = document.getElementById('eval-run');
	var evalStatus = document.getElementById('eval-status');
	var evalTable = document.getElementById('eval-table');
	var evalTbody = document.getElementById('eval-tbody');

	evalRun.addEventListener('click', function () {
		evalRun.disabled = true;
		evalStatus.className = '';
		evalStatus.textContent = 'Menjalankan 100 query dev untuk mode ' + evalMode.value + ' ...';
		var url = EVAL_URL + '?mode=' + encodeURIComponent(evalMode.value);
		var mulai = Date.now();
		fetch(url, { credentials: 'same-origin' })
			.then(function (resp) {
				if (!resp.ok) {
					return resp.json().catch(function () { return {}; }).then(function (body) {
						throw new Error(body.error || ('HTTP ' + resp.status));
					});
				}
				var waktuHeader = resp.headers.get('X-Unifiedsearch-Waktu-Ms');
				return resp.json().then(function (data) { data._waktuMs = waktuHeader; return data; });
			})
			.then(function (data) {
				var row = '<tr>' +
					'<td>' + escapeHtml(data.mode) + '</td>' +
					'<td>' + data.p1.toFixed(3) + '</td>' +
					'<td>' + data.p5.toFixed(3) + '</td>' +
					'<td>' + data.r10.toFixed(3) + '</td>' +
					'<td>' + data.mrr.toFixed(3) + '</td>' +
					'<td>' + data.map.toFixed(3) + '</td>' +
					'<td>' + data.ndcg10.toFixed(4) + '</td>' +
					'<td>' + data.pct_nol.toFixed(1) + '%</td>' +
					'<td>' + (data._waktuMs !== null ? data._waktuMs : '?') + '</td>' +
					'</tr>';
				evalTbody.innerHTML = row;
				evalTable.style.display = '';
				evalStatus.textContent = 'Selesai (' + (Date.now() - mulai) + ' ms lewat jaringan). gold_sha256=' + data.gold_sha256;
			})
			.catch(function (err) {
				evalStatus.className = 'us-error';
				evalStatus.textContent = 'Galat: ' + err.message;
			})
			.then(function () {
				evalRun.disabled = false;
			});
	});
})();
</script>

<%@ include file="/WEB-INF/template/footer.jsp" %>
