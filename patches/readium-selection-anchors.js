/* Source endpoints for new EPUB selections. Readium continues to render and navigate them. */
window.__readleAnchors = (() => {
    const pathTo = node => {
        const path = [];
        while (node !== document.body) {
            const parent = node.parentNode;
            if (!parent) return null;
            path.unshift(Array.prototype.indexOf.call(parent.childNodes, node));
            node = parent;
        }
        return path;
    };

    function capture() {
        const result = readium.getCurrentSelection();
        const selection = window.getSelection();
        if (!result || !selection || !selection.rangeCount) return result;
        const range = selection.getRangeAt(0);
        const start = pathTo(range.startContainer);
        const end = pathTo(range.endContainer);
        if (start && end) {
            result.readleRange = {
                version: 1,
                start,
                end,
                startOffset: range.startOffset,
                endOffset: range.endOffset,
            };
        }
        return result;
    }

    function resolve(locator) {
        const saved = locator.locations && locator.locations.readleRange;
        if (!saved || saved.version !== 1) return null;
        try {
            const nodeAt = path => path.reduce((node, i) => node.childNodes[i], document.body);
            const range = document.createRange();
            range.setStart(nodeAt(saved.start), saved.startOffset);
            range.setEnd(nodeAt(saved.end), saved.endOffset);
            // Native selection adds layout whitespace between lines/blocks. DOM ranges do not.
            const compact = value => value.replace(/\s/gu, "");
            return !range.collapsed &&
                compact(range.toString()) === compact(locator.text.highlight) ? range : null;
        } catch (_) {
            return null;
        }
    }

    // Chromium can discard the touch handles when its WebView loses focus. Recreate
    // an adjustable range only if the exact source is visible again and no range is live.
    function resume(locator, scrolling) {
        const selection = window.getSelection();
        const visible = range => {
            const b = range.getBoundingClientRect();
            return b.right > 0 && b.left < innerWidth && b.bottom > 0 && b.top < innerHeight;
        };
        // Leave the snapshot armed while WebView still owns a range. It can drop
        // that range after navigation has started, when the source is offscreen.
        if (!selection.isCollapsed && selection.toString().trim()) return false;
        const range = resolve(locator);
        if (!range || !visible(range)) return false;
        const element = tag => document.createElementNS('http://www.w3.org/1999/xhtml', tag);
        const host = element('div');
        if (!host.attachShadow || !window.PointerEvent) return false;
        // WebView can leave a collapsed caret range after dismissing the handles.
        // addRange silently ignores the new range when a caret already exists.
        selection.removeAllRanges();
        selection.addRange(range);
        if (selection.isCollapsed) return false;

        // Keep reader controls outside the body so stored text-node paths stay stable.
        // Readium's paginated root scrolls its CSS columns. Place grips in document
        // coordinates so they follow that scroll even when fixed elements do not.
        host.style.cssText = 'position:absolute;left:0;top:0;width:0;height:0;pointer-events:none;z-index:2147483646';
        const shadow = host.attachShadow({mode: 'open'});
        const style = element('style');
        style.textContent = `:root[data-readle-resumed-selection] ::selection { background: #82b9ff; color: #14253e; }`;
        document.documentElement.append(host);
        document.head.append(style);
        document.documentElement.setAttribute('data-readle-resumed-selection', '');

        const handles = {};
        let drag = null;
        let timer = 0;
        const clamp = (value, min, max) => Math.min(max, Math.max(min, value));

        function stopDrag() {
            if (drag) window.Android.onReadleHandleDrag(false);
            drag = null;
            clearInterval(timer);
        }

        function render() {
            if (selection.isCollapsed || selection.getRangeAt(0) !== range) {
                stopDrag();
                window.removeEventListener('scroll', onScroll);
                window.removeEventListener('resize', render);
                document.removeEventListener('selectionchange', render);
                window.removeEventListener('pagehide', stopDrag);
                host.remove();
                style.remove();
                document.documentElement.removeAttribute('data-readle-resumed-selection');
                return;
            }
            for (const [side, handle] of Object.entries(handles)) {
                const end = range.cloneRange();
                end.collapse(side === 'start');
                const b = end.getBoundingClientRect();
                const visible = b.left >= -1 && b.left <= innerWidth + 1 && b.bottom > 0 && b.top < innerHeight;
                // Keep the drag target attached while it holds pointer capture offscreen.
                handle.style.opacity = visible ? '1' : '0';
                handle.style.pointerEvents = visible ? 'auto' : 'none';
                handle.style.left = `${scrollX + clamp(b.left, 17, innerWidth - 18)}px`;
                handle.style.top = `${scrollY + clamp(b.bottom + 10, 17, innerHeight - 18)}px`;
            }
        }

        function move() {
            if (!drag) return;
            host.style.visibility = 'hidden';
            const caret = document.caretRangeFromPoint(
                clamp(drag.x, 1, innerWidth - 2), clamp(drag.y - drag.offset, 1, innerHeight - 2));
            host.style.visibility = '';
            if (!caret || !document.body.contains(caret.startContainer)) return;
            const next = range.cloneRange();
            if (drag.side === 'start') next.setStart(caret.startContainer, caret.startOffset);
            else next.setEnd(caret.startContainer, caret.startOffset);
            if (next.collapsed || !next.toString().trim()) return;
            if (drag.side === 'start') range.setStart(next.startContainer, next.startOffset);
            else range.setEnd(next.endContainer, next.endOffset);
            render();
        }

        function autoAdvance() {
            if (!drag) return;
            const offset = scrolling ? drag.y : drag.x;
            const extent = scrolling ? innerHeight : innerWidth;
            const direction = offset < 24 ? -1 : offset > extent - 24 ? 1 : 0;
            const now = performance.now();
            if (direction !== drag.edge) {
                drag.edge = direction;
                drag.edgeSince = now;
            }
            if (!direction || now - drag.edgeSince < (scrolling ? 250 : 400)) return;
            if (scrolling) {
                window.scrollBy(0, direction * Math.max(8, Math.floor(innerHeight / 35)));
            } else if (now - drag.lastTurn >= 1000) {
                drag.lastTurn = now;
                window.Android.onReadleHandlePage(direction);
            }
        }

        for (const side of ['start', 'end']) {
            const handle = element('div');
            handle.setAttribute('aria-label', side === 'start' ? 'Selection start handle' : 'Selection end handle');
            handle.style.cssText = 'position:absolute;width:34px;height:34px;transform:translate(-50%,-50%);touch-action:none;user-select:none;display:flex;align-items:center;justify-content:center';
            const grip = element('span');
            grip.style.cssText = 'width:14px;height:14px;background:#2365c6;border:2px solid white;border-radius:50%;box-shadow:0 1px 3px #0005;pointer-events:none';
            handle.append(grip);
            shadow.append(handle);
            handles[side] = handle;
            handle.addEventListener('pointerdown', event => {
                if (drag || !event.isPrimary) return;
                event.preventDefault();
                event.stopPropagation();
                const end = range.cloneRange();
                end.collapse(side === 'start');
                const b = end.getBoundingClientRect();
                drag = {side, x: event.clientX, y: event.clientY, offset: event.clientY - (b.top + b.bottom) / 2, edge: 0, edgeSince: 0, lastTurn: -1000};
                handle.setPointerCapture(event.pointerId);
                window.Android.onReadleHandleDrag(true);
                timer = setInterval(autoAdvance, 60);
            });
            handle.addEventListener('pointermove', event => {
                if (!drag || drag.side !== side) return;
                event.stopPropagation();
                drag.x = event.clientX;
                drag.y = event.clientY;
                move();
            });
            for (const type of ['pointerup', 'pointercancel', 'lostpointercapture']) {
                handle.addEventListener(type, stopDrag);
            }
        }

        function onScroll() {
            if (drag) move();
            else render();
        }
        window.addEventListener('scroll', onScroll);
        window.addEventListener('resize', render);
        document.addEventListener('selectionchange', render);
        window.addEventListener('pagehide', stopDrag);
        render();
        return true;
    }

    return { capture, resolve, resume };
})();
