(function () {
    if (window.__CXH_ACTIVE__) { return; }
    window.__CXH_ACTIVE__ = true;

    var CFG = __CXH_CONFIG__;
    var NEXT_WORDS = ['下一节', '下一章', '下一讲', '下一课', '下一个'];
    var CONT_WORDS = ['继续学习', '继续观看', '开始学习', '重新学习', '继续播放'];
    var CLOSE_WORDS = ['知道了', '我知道了', '关闭'];
    var nextRequestAt = 0;
    var lastPostAt = 0;
    var IS_COURSE = /mooc|course|chapter|knowledge|ananas|video|study|exam/i.test(location.href);

    function now() { return Date.now(); }

    function textOf(el) {
        if (!el) { return ''; }
        var t = el.innerText || el.textContent || '';
        return (t + '').replace(/\s+/g, '');
    }

    function isVisible(el) {
        if (!el || !el.getBoundingClientRect) { return false; }
        var r = el.getBoundingClientRect();
        if (r.width < 2 || r.height < 2) { return false; }
        try {
            var s = window.getComputedStyle ? window.getComputedStyle(el) : null;
            if (s) {
                if (s.display === 'none' || s.visibility === 'hidden') { return false; }
                if (parseFloat(s.opacity || '1') < 0.05) { return false; }
            }
        } catch (e) { }
        return true;
    }

    function clickIn(doc, words, maxLen) {
        if (!doc || !doc.querySelectorAll) { return false; }
        var nodes;
        try {
            nodes = doc.querySelectorAll('a,button,[role="button"],span,div,li,p');
        } catch (e) {
            return false;
        }
        var limit = Math.min(nodes.length, 3500);
        for (var i = 0; i < limit; i++) {
            var el = nodes[i];
            var t = textOf(el);
            if (!t || t.length > maxLen) { continue; }
            for (var j = 0; j < words.length; j++) {
                if (t.indexOf(words[j]) < 0) { continue; }
                if (!isVisible(el)) { continue; }
                try {
                    if (el.click) {
                        el.click();
                        return true;
                    }
                } catch (e) { }
            }
        }
        return false;
    }

    function allDocs() {
        var out = [];
        var walk = function (w, depth) {
            if (depth > 5) { return; }
            var doc = null;
            try { doc = w.document; } catch (e) { return; }
            if (doc) { out.push(doc); }
            var n = 0;
            try { n = w.frames.length; } catch (e) { return; }
            for (var i = 0; i < n; i++) {
                try { walk(w.frames[i], depth + 1); } catch (e) { }
            }
        };
        walk(window, 0);
        return out;
    }

    function fixVideo(v) {
        if (!v) { return false; }
        try {
            if (!v.__cxhListen) {
                v.__cxhListen = 1;
                v.addEventListener('ended', function () {
                    window.__cxhEndedAt = now();
                    if (window !== window.top) {
                        try { window.top.postMessage({ __cxh_next: 1 }, '*'); } catch (e) { }
                    }
                }, false);
            }
            if (CFG.mute) {
                v.muted = true;
                v.volume = 0;
            }
            if (CFG.rate > 0) {
                if (Math.abs(v.playbackRate - CFG.rate) > 0.01) {
                    v.playbackRate = CFG.rate;
                }
            }
            if (CFG.jumpEnd && v.duration > 0 && !v.__cxhJumped && v.currentTime < v.duration - 3) {
                v.__cxhJumped = true;
                try { v.currentTime = Math.max(0, v.duration - 1); } catch (e) { }
            }
            if (v.paused && !v.ended) {
                var p = v.play();
                if (p && p.catch) { p.catch(function () { }); }
            }
        } catch (e) { }
        return v.ended || (v.duration > 0 && v.currentTime >= v.duration - 0.8);
    }

    function scanDoc(doc, isTop) {
        if (!doc) { return false; }
        var vids = [];
        try { vids = doc.getElementsByTagName('video') || []; } catch (e) { }
        var ended = false;
        for (var i = 0; i < vids.length; i++) {
            if (fixVideo(vids[i])) { ended = true; }
        }

        if (!isTop) {
            if (ended && now() - lastPostAt > 3000) {
                lastPostAt = now();
                try { window.top.postMessage({ __cxh_next: 1 }, '*'); } catch (e) { }
            }
            if (IS_COURSE && CFG.autoContinue && isVisible(doc.body) && now() - (doc.__cxhClickAt || 0) > 2500) {
                if (clickIn(doc, CONT_WORDS, 10)) { doc.__cxhClickAt = now(); }
            }
        }

        if (CFG.quizGuess) {
            guessQuiz(doc);
        }
        return ended;
    }

    function guessQuiz(doc) {
        if (!doc || !doc.querySelectorAll) { return; }
        var boxes = [];
        try {
            boxes = doc.querySelectorAll('[class*="quiz"],[class*="question"],[class*="popup"],[class*="TiMu"]');
        } catch (e) { return; }
        for (var i = 0; i < boxes.length && i < 6; i++) {
            var box = boxes[i];
            if (!isVisible(box) || box.__cxhQuiz) { continue; }
            var opts = box.querySelectorAll('input[type="radio"],label,li');
            if (!opts.length) { continue; }
            box.__cxhQuiz = 1;
            try { opts[0].click(); } catch (e) { }
            (function (b) {
                setTimeout(function () {
                    clickIn(b, ['提交', '确定', '确认'], 6);
                }, 500);
            })(box);
        }
    }

    function tryNext() {
        if (!CFG.autoNext) { return; }
        if (now() - (document.__cxhLastNext || 0) < 5000) { return; }
        var docs = allDocs();
        for (var i = 0; i < docs.length; i++) {
            if (clickIn(docs[i], NEXT_WORDS, 10)) {
                document.__cxhLastNext = now();
                return;
            }
        }
    }

    function tick() {
        var top = window;
        try { top = window.top; } catch (e) { }

        var isTop = false;
        try { isTop = (window === window.top); } catch (e) { isTop = false; }

        if (isTop) {
            var docs = allDocs();
            var anyEnded = false;
            for (var i = 0; i < docs.length; i++) {
                if (scanDoc(docs[i], docs[i] === document)) { anyEnded = true; }
            }
            if (IS_COURSE && CFG.autoContinue) {
                for (var j = 0; j < docs.length; j++) {
                    var d = docs[j];
                    if (now() - (d.__cxhClickAt || 0) <= 2500) { continue; }
                    if (clickIn(d, CONT_WORDS, 10)) { d.__cxhClickAt = now(); break; }
                }
            }
            if (IS_COURSE && CFG.autoClose && now() - (document.__cxhCloseAt || 0) > 4000) {
                if (clickIn(document, CLOSE_WORDS, 8)) {
                    document.__cxhCloseAt = now();
                }
            }
            if (IS_COURSE && (anyEnded || now() - nextRequestAt < 6000
                    || now() - (window.__cxhEndedAt || 0) < 15000)) {
                tryNext();
            }
        } else {
            scanDoc(document, false);
        }
    }

    try {
        window.addEventListener('message', function (e) {
            if (e && e.data && e.data.__cxh_next) {
                nextRequestAt = now();
            }
        }, false);
    } catch (e) { }

    try {
        var badge = document.createElement('div');
        badge.textContent = '助手:' + (window === window.top ? 'TOP' : 'IFRAME');
        badge.style.cssText = 'position:fixed;right:8px;top:8px;z-index:2147483647;'
            + 'background:rgba(0,0,0,.78);color:#fff;font:12px/1.5 sans-serif;'
            + 'padding:3px 7px;border-radius:5px;pointer-events:none';
        (document.body || document.documentElement).appendChild(badge);
        setTimeout(function () {
            if (badge.parentNode) { badge.parentNode.removeChild(badge); }
        }, 5000);
    } catch (e) { }

    setInterval(tick, 1500);
    setTimeout(tick, 300);
    return 'cxh-ok';
})();