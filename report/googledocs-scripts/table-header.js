function colorTableHeaders() {
    // ===== 사용자 설정 (이 함수 전용) =====
    const DOC_ID            = '1urPdY1X8TuJHF52FI-KJX-jlGRbSrw18F0lIcUUyZEE';
    const HEADER_COLOR_HEX  = '#D9E1F2';
    const HEADER_TARGET     = 'FIRST_ROW';   // 'FIRST_ROW' | 'FIRST_COLUMN' | 'BOTH'
    const HEADER_ROW_INDEX  = 0;
    const HEADER_COL_INDEX  = 0;
    const SKIP_BEFORE_BODY  = true;
    const HEADER_ALIGN      = 'CENTER';      // 색상 입힌 셀의 단락 정렬
    const HEADER_BOLD       = true;          // 색상 입힌 셀의 텍스트 볼드 처리
    const HEADER_FONT_SIZE  = 11;            // 색상 입힌 셀(헤더)의 폰트 크기 (pt, 0 이면 미적용)
    const BODY_FONT_SIZE    = 10;            // 본문(헤더 외) 행의 폰트 크기 (pt) — 너비 추정용
    const SKIP_FIRST_TABLE  = true;          // bodyStart 이후 만나는 첫 번째 표는 제외
    const TABLE_BORDER      = true;          // 각 표에 테두리 적용
    const BORDER_COLOR_HEX  = '#000000';     // 테두리 색 (검은색)
    const BORDER_WIDTH_PT   = 1.0;           // 테두리 굵기 (pt)
    const AUTOFIT_COLUMNS   = true;          // 표 열 너비를 내용(헤더 포함)에 맞춰 자동 조정
    const MIN_COL_WIDTH_PT  = 40;            // 열 최소 너비 (pt)
    const CHAR_WIDTH_RATIO  = 0.6;           // 글자 너비 ≈ 폰트 크기(pt) × 비율 (너비 추정용)
    const CELL_PADDING_PT   = 10;            // 셀 좌우 여백 보정 (pt)
    const PRIORITY_COL_INDEX = 0;            // 너비를 우선 확보할 열 (id 열 등). -1 이면 미사용
    const PRIORITY_MAX_FRAC  = 0.5;          // 우선 열이 가져갈 수 있는 가용 폭의 최대 비율
    const WIDTH_SAFETY_PT    = 1;            // 인쇄 경계 침범 방지용 가용 폭 안전 여백 (pt)
    const UNIFY_PADDING     = true;          // 모든 셀 여백을 동일 값으로 통일·최소화
    const CELL_PAD_H_PT     = 4;             // 셀 좌우 여백 (pt) — 가독성 유지 최소값
    const CELL_PAD_V_PT     = 3;             // 셀 상하 여백 (pt) — 답답하지 않게 약간 확보
    const ROW_OVERFLOW      = true;          // 행이 페이지 경계를 넘어 나뉘는 것 허용 (preventOverflow=false)
    const COMPACT_LINES     = true;          // 셀 내 단락 줄간격·단락간격을 최소화 (세로 공백 축소)
    const LINE_SPACING_PCT  = 115;           // 줄 간격 (%, 100 = 단일 / Docs 기본 115)
    const PARA_SPACE_PT     = 0;             // 단락 위/아래 간격 (pt)
    // =====================================

    const doc = Docs.Documents.get(DOC_ID);
    const rgb = hexToRgb(HEADER_COLOR_HEX);
    const borderRgb = hexToRgb(BORDER_COLOR_HEX);
    const bodyStart = SKIP_BEFORE_BODY ? findBodyStart(doc.body.content) : 0;

    const requests = [];
    let tableCount = 0;
    let borderCount = 0;
    let autofitCount = 0;
    let tablesSeen = 0;
    walk(doc.body.content);

    if (!requests.length) {
        Logger.log('대상 표가 없습니다.');
        return;
    }

    // 분당 쓰기 쿼터(write operations per minute)를 피하려면 batchUpdate 호출 수를 최소화한다.
    // 한 번의 batchUpdate 에 요청을 크게 묶고, 배치가 여러 개면 사이에 잠깐 sleep 한다.
    const CHUNK = 1000;
    for (let i = 0; i < requests.length; i += CHUNK) {
        if (i > 0) Utilities.sleep(1200);
        Docs.Documents.batchUpdate({ requests: requests.slice(i, i + CHUNK) }, DOC_ID);
    }
    Logger.log('표 ' + tableCount + '개 / 요청 ' + requests.length + '건 적용 (배경 ' + HEADER_COLOR_HEX +
        ', 정렬 ' + HEADER_ALIGN + ', 볼드 ' + HEADER_BOLD +
        ', 테두리 ' + (TABLE_BORDER ? (borderCount + '개 표, ' + BORDER_COLOR_HEX + ' ' + BORDER_WIDTH_PT + 'pt') : '미적용') +
        ', 너비 자동맞춤 ' + (AUTOFIT_COLUMNS ? (autofitCount + '개 표') : '미적용') +
        ', 여백 ' + (UNIFY_PADDING ? ('통일 좌우' + CELL_PAD_H_PT + '·상하' + CELL_PAD_V_PT + 'pt') : '미적용') +
        ', 행 오버플로 ' + (ROW_OVERFLOW ? '허용' : '미적용') +
        ', 줄간격압축 ' + (COMPACT_LINES ? (LINE_SPACING_PCT + '%·단락' + PARA_SPACE_PT + 'pt') : '미적용') + ')');

    function walk(content) {
        if (!content) return;
        for (const el of content) {
            if (el.table && el.startIndex >= bodyStart) {
                tablesSeen++;
                const skipThisTable = SKIP_FIRST_TABLE && tablesSeen === 1;

                // 테두리: 본문의 모든 표에 적용 (헤더 색칠 제외 여부와 무관)
                if (TABLE_BORDER) {
                    const bRows = el.table.rows;
                    const bCols = el.table.columns;
                    if (bRows > 0 && bCols > 0) {
                        borderCount++;
                        requests.push(makeBorderReq(el.startIndex, bRows, bCols));
                    }
                }

                // 열 너비 자동 맞춤: 본문의 모든 표에 적용
                if (AUTOFIT_COLUMNS) {
                    if (applyAutofit(el.startIndex, el.table)) autofitCount++;
                }

                // 셀 여백 통일·최소화: 본문의 모든 표에 적용
                if (UNIFY_PADDING) {
                    const pRows = el.table.rows;
                    const pCols = el.table.columns;
                    if (pRows > 0 && pCols > 0) {
                        requests.push(makePaddingReq(el.startIndex, pRows, pCols));
                    }
                }

                // 행의 페이지 경계 오버플로 허용 + 최소 높이 해제: 본문의 모든 표에 적용
                if (ROW_OVERFLOW) {
                    const oRows = el.table.rows;
                    if (oRows > 0) {
                        requests.push(makeRowOverflowReq(el.startIndex, oRows));
                    }
                }

                // 셀 내 단락 줄간격·단락간격 압축(세로 공백 축소): 본문의 모든 표에 적용
                if (COMPACT_LINES) {
                    applyCompactCells(el.table);
                }

                if (!skipThisTable) {
                    const numRows = el.table.rows;
                    const numCols = el.table.columns;
                    if (numRows > HEADER_ROW_INDEX && numCols > HEADER_COL_INDEX) {
                        tableCount++;
                        if (HEADER_TARGET === 'FIRST_ROW' || HEADER_TARGET === 'BOTH') {
                            requests.push(makeStyleReq(el.startIndex, HEADER_ROW_INDEX, 0, 1, numCols));
                            decorateRow(el.table, HEADER_ROW_INDEX);
                        }
                        if (HEADER_TARGET === 'FIRST_COLUMN' || HEADER_TARGET === 'BOTH') {
                            requests.push(makeStyleReq(el.startIndex, 0, HEADER_COL_INDEX, numRows, 1));
                            decorateColumn(el.table, HEADER_COL_INDEX);
                        }
                    }
                }

                for (const row of el.table.tableRows || []) {
                    for (const cell of row.tableCells || []) walk(cell.content);
                }
            }
        }
    }

    function makeStyleReq(startIdx, rowIdx, colIdx, rowSpan, colSpan) {
        return {
            updateTableCellStyle: {
                tableRange: {
                    tableCellLocation: {
                        tableStartLocation: { index: startIdx },
                        rowIndex: rowIdx,
                        columnIndex: colIdx
                    },
                    rowSpan: rowSpan,
                    columnSpan: colSpan
                },
                // 배경색 + 세로 가운데 정렬(MIDDLE). 가로 가운데는 decorateCell 의 단락 정렬로 적용된다.
                tableCellStyle: {
                    backgroundColor: { color: { rgbColor: rgb } },
                    contentAlignment: 'MIDDLE'
                },
                fields: 'backgroundColor,contentAlignment'
            }
        };
    }

    function makeBorderReq(startIdx, numRows, numCols) {
        const border = {
            color: { color: { rgbColor: borderRgb } },
            width: { magnitude: BORDER_WIDTH_PT, unit: 'PT' },
            dashStyle: 'SOLID'
        };
        return {
            updateTableCellStyle: {
                tableRange: {
                    tableCellLocation: {
                        tableStartLocation: { index: startIdx },
                        rowIndex: 0,
                        columnIndex: 0
                    },
                    rowSpan: numRows,
                    columnSpan: numCols
                },
                tableCellStyle: {
                    borderTop: border,
                    borderBottom: border,
                    borderLeft: border,
                    borderRight: border
                },
                fields: 'borderTop,borderBottom,borderLeft,borderRight'
            }
        };
    }

    // 표 전체 셀의 여백을 동일 값으로 통일·최소화
    function makePaddingReq(startIdx, numRows, numCols) {
        const h = { magnitude: CELL_PAD_H_PT, unit: 'PT' };
        const v = { magnitude: CELL_PAD_V_PT, unit: 'PT' };
        return {
            updateTableCellStyle: {
                tableRange: {
                    tableCellLocation: {
                        tableStartLocation: { index: startIdx },
                        rowIndex: 0,
                        columnIndex: 0
                    },
                    rowSpan: numRows,
                    columnSpan: numCols
                },
                tableCellStyle: {
                    paddingLeft: h,
                    paddingRight: h,
                    paddingTop: v,
                    paddingBottom: v
                },
                fields: 'paddingLeft,paddingRight,paddingTop,paddingBottom'
            }
        };
    }

    // 표 전체 행이 페이지 경계를 넘어 나뉘도록 허용(preventOverflow=false)하고
    // 강제 최소 높이를 제거(minimumHeight=0)해 불필요한 세로 공백을 없앤다.
    function makeRowOverflowReq(startIdx, numRows) {
        const rowIndices = [];
        for (let r = 0; r < numRows; r++) rowIndices.push(r);
        return {
            updateTableRowStyle: {
                tableStartLocation: { index: startIdx },
                rowIndices: rowIndices,
                tableRowStyle: {
                    preventOverflow: false,
                    minRowHeight: { magnitude: 0, unit: 'PT' }
                },
                fields: 'preventOverflow,minRowHeight'
            }
        };
    }

    // 표 전체 셀 단락의 줄 간격·단락 위아래 간격을 최소화해 세로 공백을 줄인다.
    function applyCompactCells(table) {
        for (const row of (table.tableRows || [])) {
            for (const cell of (row.tableCells || [])) {
                for (const el of (cell.content || [])) {
                    if (!el.paragraph) continue;
                    if (typeof el.startIndex !== 'number' || typeof el.endIndex !== 'number') continue;
                    requests.push({
                        updateParagraphStyle: {
                            range: { startIndex: el.startIndex, endIndex: el.endIndex },
                            paragraphStyle: {
                                lineSpacing: LINE_SPACING_PCT,
                                spaceAbove: { magnitude: PARA_SPACE_PT, unit: 'PT' },
                                spaceBelow: { magnitude: PARA_SPACE_PT, unit: 'PT' }
                            },
                            fields: 'lineSpacing,spaceAbove,spaceBelow'
                        }
                    });
                }
            }
        }
    }

    // 열별 콘텐츠(헤더 행 포함) 길이를 측정해 페이지 가용 폭에 비례 배분 → FIXED_WIDTH 로 지정.
    // id 같은 긴 고유값 열은 자연스럽게 더 넓은 폭을 받아 가독성이 확보된다.
    function applyAutofit(startIdx, table) {
        const cols = table.columns;
        const rows = table.tableRows || [];
        if (!cols || cols < 1 || !rows.length) return false;

        // 1) 열별 자연 너비 측정 — 행마다 폰트 크기가 다르므로(헤더 11pt·본문 10pt)
        //    각 셀을 (시각 길이 × 폰트크기 × 비율 + 여백)로 환산해 최댓값을 취한다.
        //    한글·전각 문자는 시각 폭 2로 계산한다.
        const natural = new Array(cols).fill(MIN_COL_WIDTH_PT);
        for (let r = 0; r < rows.length; r++) {
            const isHeaderRow = (r === HEADER_ROW_INDEX);
            const fontPt = isHeaderRow ? (HEADER_FONT_SIZE || BODY_FONT_SIZE) : BODY_FONT_SIZE;
            const cells = rows[r].tableCells || [];
            for (let c = 0; c < cells.length && c < cols; c++) {
                const w = visualLen(getCellText(cells[c])) * fontPt * CHAR_WIDTH_RATIO + CELL_PADDING_PT;
                if (w > natural[c]) natural[c] = w;
            }
        }

        // 2) 가용 폭에 맞춰 너비 배분
        const usable = usableContentWidth();
        const widths = distributeWidths(natural, usable, cols);

        for (let c = 0; c < cols; c++) {
            requests.push({
                updateTableColumnProperties: {
                    tableStartLocation: { index: startIdx },
                    columnIndices: [c],
                    tableColumnProperties: {
                        widthType: 'FIXED_WIDTH',
                        width: { magnitude: widths[c], unit: 'PT' }
                    },
                    fields: 'widthType,width'
                }
            });
        }
        return true;
    }

    // 자연 너비를 가용 폭에 배분한다.
    // 우선 열(PRIORITY_COL_INDEX)이 있으면 그 열의 내용을 담을 폭을 먼저 확보(가용 폭의
    // PRIORITY_MAX_FRAC 한도)한 뒤, 남는 폭을 나머지 열에 자연 너비 비례로 분배한다.
    // → id 같은 첫 열이 다른 열에 밀려 세로로 줄바꿈되는 현상을 막는다.
    function distributeWidths(natural, usable, cols) {
        const p = PRIORITY_COL_INDEX;
        const usePriority = (p >= 0 && p < cols && cols > 1);

        let widths;
        if (!usePriority) {
            const sum = natural.reduce((a, b) => a + b, 0);
            const scale = sum > 0 ? usable / sum : 1;
            widths = natural.map(w => Math.max(MIN_COL_WIDTH_PT, w * scale));
        } else {
            // 나머지 열의 최소 폭 합 — 우선 열이 이보다 많이 가져가지 못하게 한다.
            const restCount = cols - 1;
            const restMinTotal = MIN_COL_WIDTH_PT * restCount;

            // 우선 열 너비: 내용을 다 담되, 가용 폭의 일정 비율 + 나머지 최소폭 확보 한도 안에서
            let pWidth = Math.min(natural[p], usable * PRIORITY_MAX_FRAC, usable - restMinTotal);
            pWidth = Math.max(MIN_COL_WIDTH_PT, pWidth);

            // 남은 폭을 나머지 열에 자연 너비 비례로 분배
            let restNatural = 0;
            for (let c = 0; c < cols; c++) if (c !== p) restNatural += natural[c];
            const remaining = Math.max(restMinTotal, usable - pWidth);
            const rscale = restNatural > 0 ? remaining / restNatural : 1;

            widths = new Array(cols);
            widths[p] = pWidth;
            for (let c = 0; c < cols; c++) {
                if (c === p) continue;
                widths[c] = Math.max(MIN_COL_WIDTH_PT, natural[c] * rscale);
            }
        }

        // 최종 안전장치: 합계가 가용 폭을 넘으면 인쇄 경계를 침범하므로 전체를 비례 축소한다.
        // (우선 열 보장·최소폭 적용 과정에서 합이 부풀 수 있어 반드시 클램프)
        const total = widths.reduce((a, b) => a + b, 0);
        if (total > usable && total > 0) {
            const fit = usable / total;
            for (let c = 0; c < cols; c++) widths[c] = widths[c] * fit;
        }
        return widths;
    }

    // 셀 안의 모든 단락 텍스트를 한 줄로 합쳐 반환
    function getCellText(cell) {
        let out = '';
        for (const el of (cell.content || [])) {
            if (!el.paragraph) continue;
            for (const pe of (el.paragraph.elements || [])) {
                if (pe.textRun && pe.textRun.content) out += pe.textRun.content;
            }
        }
        return out.replace(/\s+/g, ' ').trim();
    }

    // 시각 폭 계산: 한글·CJK·전각 문자는 2, 그 외는 1
    function visualLen(s) {
        if (!s) return 0;
        let n = 0;
        for (const ch of s) {
            const code = ch.codePointAt(0);
            const wide =
                (code >= 0x1100 && code <= 0x115F) ||   // 한글 자모
                (code >= 0x2E80 && code <= 0xA4CF) ||   // CJK 부수·한자·가나 등
                (code >= 0xAC00 && code <= 0xD7A3) ||   // 한글 음절
                (code >= 0xF900 && code <= 0xFAFF) ||   // CJK 호환 한자
                (code >= 0xFF00 && code <= 0xFF60) ||   // 전각 형태
                (code >= 0xFFE0 && code <= 0xFFE6);
            n += wide ? 2 : 1;
        }
        return n;
    }

    // 본문 가용 폭(페이지 폭 − 좌우 여백 − 안전 여백) 계산.
    // 값이 없으면 A4(595.276pt) 기본값으로 — 인쇄 가능 범위를 넘지 않도록.
    function usableContentWidth() {
        const ds = doc.documentStyle || {};
        const pw = ds.pageSize && ds.pageSize.width ? ds.pageSize.width.magnitude : 595.276;
        const ml = ds.marginLeft ? ds.marginLeft.magnitude : 72;
        const mr = ds.marginRight ? ds.marginRight.magnitude : 72;
        return Math.max(120, pw - ml - mr - WIDTH_SAFETY_PT);
    }

    function decorateRow(table, rowIdx) {
        const row = (table.tableRows || [])[rowIdx];
        if (!row) return;
        for (const cell of row.tableCells || []) decorateCell(cell);
    }

    function decorateColumn(table, colIdx) {
        for (const row of table.tableRows || []) {
            const cell = (row.tableCells || [])[colIdx];
            if (cell) decorateCell(cell);
        }
    }

    function decorateCell(cell) {
        for (const el of cell.content || []) {
            if (!el.paragraph) continue;
            if (typeof el.startIndex !== 'number' || typeof el.endIndex !== 'number') continue;

            // 단락 정렬
            requests.push({
                updateParagraphStyle: {
                    range: { startIndex: el.startIndex, endIndex: el.endIndex },
                    paragraphStyle: { alignment: HEADER_ALIGN },
                    fields: 'alignment'
                }
            });

            // 적용할 텍스트 스타일(볼드·폰트 크기) 구성
            const textStyle = {};
            const fieldList = [];
            if (HEADER_BOLD) {
                textStyle.bold = true;
                fieldList.push('bold');
            }
            if (HEADER_FONT_SIZE > 0) {
                textStyle.fontSize = { magnitude: HEADER_FONT_SIZE, unit: 'PT' };
                fieldList.push('fontSize');
            }
            if (!fieldList.length) continue;

            // 텍스트 스타일 — 단락 종결 \n 은 마지막 textRun 에서만 잘라낸다
            const elements = el.paragraph.elements || [];
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
                requests.push({
                    updateTextStyle: {
                        range: { startIndex: start, endIndex: end },
                        textStyle: textStyle,
                        fields: fieldList.join(',')
                    }
                });
            }
        }
    }

    function hexToRgb(hex) {
        const m = /^#?([0-9a-fA-F]{6})$/.exec(hex);
        if (!m) throw new Error('잘못된 색상 형식: ' + hex + ' (#RRGGBB)');
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
                const isHeading = st && /^HEADING_[1-3]$/.test(st.namedStyleType || '');
                if (isHeading) {
                    const txt = (el.paragraph.elements || [])
                        .map(pe => pe.textRun ? pe.textRun.content : '').join('');
                    if (/시스템\s*개요/.test(txt)) return el.startIndex;
                }
            }
        }
        return 0;
    }
}