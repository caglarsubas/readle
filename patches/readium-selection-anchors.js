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
        const saved = locator.locations.readleRange;
        if (saved.version !== 1) return null;
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

    return { capture, resolve };
})();
