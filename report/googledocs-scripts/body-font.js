function setBodyFontSize() {
    // ===== 사용자 설정 =====
    const DOC_ID           = '1urPdY1X8TuJHF52FI-KJX-jlGRbSrw18F0lIcUUyZEE';
    const BODY_FONT_PT     = 10;
    const SKIP_BEFORE_BODY = true;   // 표지·Revision History·TOC 보호
    const APPLY_TABLE_BODY = true;   // 표 본문 셀(헤더 0행 제외)에도 동일 크기 적용
    const HEADER_ROW_INDEX = 0;      // 표 헤더 행 인덱스 (table-header.js 와 동일하게 유지)
    // ======================

    const doc = Docs.Documents.get(DOC_ID);
    const bodyStart = SKIP_BEFORE_BODY ? findBodyStart(doc.body.content) : 0;

    const requests = [];
    let paraCount = 0;

    walk(doc.body.content);

    if (!requests.length) {
        Logger.log('대상 단락이 없습니다. bodyStart=' + bodyStart);
        return;
    }

    const CHUNK = 500;
    for (let i = 0; i < requests.length; i += CHUNK) {
        if (i > 0) Utilities.sleep(1200);
        Docs.Documents.batchUpdate({ requests: requests.slice(i, i + CHUNK) }, DOC_ID);
    }
    Logger.log('단락 ' + paraCount + '개 / textStyle 갱신 ' + requests.length + '건 (' + BODY_FONT_PT + 'pt 적용)');

    // ---- 내부 함수 ----

    function walk(content) {
        if (!content) return;
        for (const el of content) {
            if (el.paragraph) {
                const nst = el.paragraph.paragraphStyle && el.paragraph.paragraphStyle.namedStyleType;
                // NORMAL_TEXT 단락만 대상 (헤딩은 header-color.js 에서 별도 처리)
                if (nst === 'NORMAL_TEXT' && el.startIndex >= bodyStart) {
                    paraCount++;
                    styleElements(el.paragraph.elements || []);
                }
            } else if (el.table && el.startIndex >= bodyStart) {
                if (APPLY_TABLE_BODY) {
                    // 표 본문 셀 (헤더 행 HEADER_ROW_INDEX 제외)
                    const rows = el.table.tableRows || [];
                    for (let r = 0; r < rows.length; r++) {
                        if (r === HEADER_ROW_INDEX) continue;   // 헤더 행은 table-header.js 가 처리
                        for (const cell of rows[r].tableCells || []) walk(cell.content);
                    }
                }
            }
        }
    }

    function styleElements(elements) {
        // 단락 종결 \n 은 마지막 textRun 에서만 1칸 잘라낸다
        let lastTextRunIdx = -1;
        for (let i = elements.length - 1; i >= 0; i--) {
            if (elements[i].textRun) { lastTextRunIdx = i; break; }
        }
        for (let i = 0; i < elements.length; i++) {
            const pe = elements[i];
            if (!pe.textRun) continue;
            let start = pe.startIndex;
            let end   = pe.endIndex;
            if (i === lastTextRunIdx) {
                if ((pe.textRun.content || '').endsWith('\n')) end = end - 1;
            }
            if (start == null || end == null || start >= end) continue;
            requests.push({
                updateTextStyle: {
                    range: { startIndex: start, endIndex: end },
                    textStyle: { fontSize: { magnitude: BODY_FONT_PT, unit: 'PT' } },
                    fields: 'fontSize'
                }
            });
        }
    }

    function findBodyStart(content) {
        for (const el of content || []) {
            if (el.paragraph) {
                const st = el.paragraph.paragraphStyle;
                const nst = st && st.namedStyleType;
                if (nst && /^HEADING_[1-3]$/.test(nst)) {
                    const txt = (el.paragraph.elements || [])
                        .map(pe => pe.textRun ? pe.textRun.content : '').join('');
                    if (/시스템\s*개요/.test(txt)) return el.startIndex;
                }
            }
        }
        return 0;
    }
}
