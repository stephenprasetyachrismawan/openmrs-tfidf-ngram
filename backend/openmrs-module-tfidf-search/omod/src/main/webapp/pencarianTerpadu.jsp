<%@ include file="/WEB-INF/template/include.jsp" %>
<%@ include file="/WEB-INF/template/header.jsp" %>

<h2><spring:message code="unifiedsearch.page.heading"/></h2>

<p><spring:message code="unifiedsearch.page.placeholder"/></p>

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

<%@ include file="/WEB-INF/template/footer.jsp" %>
