function colorAllHeadings() {
    // ===== 사용자 설정 (함수 내부) =====
    const DOC_ID            = '1urPdY1X8TuJHF52FI-KJX-jlGRbSrw18F0lIcUUyZEE';
    const COLOR_HEX         = '#4f81bd';
    const TARGET_LEVELS     = /^HEADING_[1-6]$/;   // H1 ~ H6 (변경 가능)
    const SKIP_BEFORE_BODY  = true;                // 표지·Revision History·TOC 보호
    const LEVEL_FONT_SIZE   = { HEADING_4: 12, HEADING_5: 11 };   // 레벨별 글자 크기(pt). 미지정 레벨은 크기 변경 안 함
    // =================================

    const doc = Docs.Documents.get(DOC_ID);
    const rgb = hexToRgb(COLOR_HEX);
    const bodyStart = SKIP_BEFORE_BODY ? findBodyStart(doc.body.content) : 0;

    const requests = [];
    let headingCount = 0;

    walk(doc.body.content);

    if (!requests.length) {
        Logger.log('대상 헤딩이 없습니다. bodyStart=' + bodyStart);
        return;
    }

    const CHUNK = 100;
    for (let i = 0; i < requests.length; i += CHUNK) {
        Docs.Documents.batchUpdate({ requests: requests.slice(i, i + CHUNK) }, DOC_ID);
    }
    Logger.log('헤딩 ' + headingCount + '개 / textStyle 갱신 ' + requests.length + '건 적용 (색상 ' + COLOR_HEX + ', 볼드)');

    // ---- 내부 함수 ----
    function walk(content) {
        if (!content) return;
        for (const el of content) {
            if (el.paragraph) {
                const st = el.paragraph.paragraphStyle;
                const nst = st && st.namedStyleType;
                if (nst && TARGET_LEVELS.test(nst) && el.startIndex >= bodyStart) {
                    headingCount++;
                    styleHeadingRuns(el.paragraph.elements || [], nst);
                }
            } else if (el.table) {
                for (const row of el.table.tableRows || []) {
                    for (const cell of row.tableCells || []) walk(cell.content);
                }
            }
        }
    }

    function styleHeadingRuns(elements, nst) {
        const sizePt = LEVEL_FONT_SIZE[nst];   // 해당 레벨 글자 크기(없으면 undefined)

        // 단락 종결 \n 은 "마지막 textRun" 에만 존재 — 그 run 에서만 1칸 잘라낸다
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
                const txt = pe.textRun.content || '';
                if (txt.endsWith('\n')) end = end - 1;
            }

            if (start == null || end == null || start >= end) continue;

            const textStyle = {
                foregroundColor: { color: { rgbColor: rgb } },
                bold: true
            };
            let fields = 'foregroundColor,bold';
            if (sizePt) {
                textStyle.fontSize = { magnitude: sizePt, unit: 'PT' };
                fields += ',fontSize';
            }

            requests.push({
                updateTextStyle: {
                    range: { startIndex: start, endIndex: end },
                    textStyle: textStyle,
                    fields: fields
                }
            });
        }
    }

    function hexToRgb(hex) {
        const m = /^#?([0-9a-fA-F]{6})$/.exec(hex);
        if (!m) throw new Error('잘못된 색상: ' + hex + ' (#RRGGBB)');
        const n = parseInt(m[1], 16);
        return {
            red:   ((n >> 16) & 0xff) / 255,
            green: ((n >> 8)  & 0xff) / 255,
            blue:  ( n        & 0xff) / 255
        };
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