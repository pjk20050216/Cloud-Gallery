/* =========================================================
 * 智能图库系统 AI 助手 · 前端
 * 纯原生 JS，无构建步骤
 * ========================================================= */
(function () {
    'use strict';

    /* ---------------- 基础 ---------------- */
    // 页面在 /api/agent-ui/index.html，接口在 /api/... 下
    const BASE = location.pathname.replace(/\/agent-ui\/.*$/, '') || '';

    const $ = (id) => document.getElementById(id);

    const state = {
        user: null,
        sessionId: null,
        sessions: [],
        agents: [],
        attachments: [],   // [{attachmentId, url, fileName}]
        streaming: false,
        currentAgent: null,
        pendingConfirm: null,
        taskTimer: null
    };

    /* ---------------- HTTP ---------------- */
    async function api(path, options = {}) {
        const resp = await fetch(BASE + path, {
            credentials: 'same-origin',
            headers: options.body && !(options.body instanceof FormData)
                ? { 'Content-Type': 'application/json' }
                : undefined,
            ...options
        });
        const text = await resp.text();
        let data;
        try {
            data = text ? JSON.parse(text) : {};
        } catch (e) {
            throw new Error('服务返回了非 JSON 内容：' + text.slice(0, 120));
        }
        if (data.code !== 0) {
            const err = new Error(data.message || ('请求失败（code=' + data.code + '）'));
            err.code = data.code;
            throw err;
        }
        return data.data;
    }

    /* ---------------- 登录 ---------------- */
    async function checkLogin() {
        try {
            const user = await api('/user/get/login');
            state.user = user;
            return true;
        } catch (e) {
            return false;
        }
    }

    $('loginForm').addEventListener('submit', async (ev) => {
        ev.preventDefault();
        const btn = $('loginBtn');
        $('loginError').textContent = '';
        btn.disabled = true;
        btn.textContent = '登录中…';
        try {
            const user = await api('/user/login', {
                method: 'POST',
                body: JSON.stringify({
                    userAccount: $('account').value.trim(),
                    userPassword: $('password').value
                })
            });
            state.user = user;
            await boot();
        } catch (e) {
            $('loginError').textContent = e.message;
        } finally {
            btn.disabled = false;
            btn.textContent = '登录';
        }
    });

    $('logoutBtn').addEventListener('click', async () => {
        try { await api('/user/logout', { method: 'POST' }); } catch (e) { /* ignore */ }
        location.reload();
    });

    /* ---------------- 启动 ---------------- */
    async function boot() {
        $('loginMask').hidden = true;
        $('app').hidden = false;
        $('userChip').textContent = '👤 ' + (state.user.userName || state.user.userAccount)
            + (state.user.userRole === 'admin' ? ' · 管理员' : '');

        await Promise.all([loadAgents(), loadSessions(), loadTasks()]);
        renderSuggestions();
        if (state.taskTimer) clearInterval(state.taskTimer);
        state.taskTimer = setInterval(() => {
            if (!document.hidden) loadTasks();
        }, 5000);
    }

    async function loadAgents() {
        try {
            state.agents = await api('/agent/agents');
            $('agentLegend').innerHTML = state.agents
                .map(a => `<span class="legend-tag" title="${escapeAttr(a.description)}">${escapeHtml(a.displayName)}</span>`)
                .join('');
        } catch (e) { /* 忽略 */ }
    }

    function renderSuggestions() {
        const box = $('suggestions');
        if (!box) return;
        const items = [
            '我有哪些空间？',
            '帮我找一下最近的图片',
            '我账号为什么被封了？',
            '你能做什么？'
        ];
        box.innerHTML = items
            .map(t => `<button class="suggestion" data-text="${escapeAttr(t)}">${escapeHtml(t)}</button>`)
            .join('');
        box.querySelectorAll('.suggestion').forEach(btn => {
            btn.addEventListener('click', () => {
                $('input').value = btn.dataset.text;
                autoResize();
                $('input').focus();
            });
        });
    }

    /* ---------------- 会话 ---------------- */
    async function loadSessions() {
        try {
            state.sessions = await api('/agent/session/list');
            renderSessions();
        } catch (e) {
            state.sessions = [];
            renderSessions();
        }
    }

    function renderSessions() {
        const box = $('sessionList');
        if (!state.sessions.length) {
            box.innerHTML = '<div class="session-item" style="color:var(--text-3);cursor:default">还没有对话</div>';
            return;
        }
        box.innerHTML = state.sessions.map(s => `
            <div class="session-item ${String(s.id) === String(state.sessionId) ? 'active' : ''}" data-id="${s.id}">
                <span class="s-title">${escapeHtml(s.title || '未命名对话')}</span>
                <button class="s-del" data-del="${s.id}" title="删除">✕</button>
            </div>
        `).join('');

        box.querySelectorAll('.session-item').forEach(el => {
            el.addEventListener('click', (ev) => {
                if (ev.target.dataset.del) return;
                openSession(el.dataset.id);
            });
        });
        box.querySelectorAll('[data-del]').forEach(btn => {
            btn.addEventListener('click', async (ev) => {
                ev.stopPropagation();
                const id = btn.dataset.del;
                if (!confirm('删除这个对话？')) return;
                try {
                    await api('/agent/session/' + id, { method: 'DELETE' });
                    if (String(state.sessionId) === String(id)) newChat();
                    await loadSessions();
                } catch (e) { alert(e.message); }
            });
        });
    }

    async function openSession(id) {
        state.sessionId = id;
        renderSessions();
        const box = $('messages');
        box.innerHTML = '<div style="text-align:center;color:var(--text-3);padding:24px">加载中…</div>';
        try {
            const msgs = await api('/agent/session/' + id + '/messages?current=1&pageSize=100');
            box.innerHTML = '';
            const s = state.sessions.find(x => String(x.id) === String(id));
            $('chatTitle').textContent = s ? (s.title || '对话') : '对话';
            if (s && s.agentType) setAgentBadge(s.agentType);

            msgs.filter(m => m.role === 'user' || m.role === 'assistant')
                .forEach(m => addMessage(m.role, m.content || ''));
            if (!msgs.length) renderEmpty();
            scrollToBottom();
        } catch (e) {
            box.innerHTML = `<div class="error-bubble">${escapeHtml(e.message)}</div>`;
        }
    }

    function newChat() {
        state.sessionId = null;
        state.attachments = [];
        state.currentAgent = null;
        $('agentBadge').hidden = true;
        $('chatTitle').textContent = '新对话';
        $('messages').innerHTML = '';
        renderEmpty();
        renderAttachments();
        renderSessions();
    }

    $('newChatBtn').addEventListener('click', newChat);

    function renderEmpty() {
        $('messages').innerHTML = `
            <div class="empty-state" id="emptyState">
                <div class="empty-icon">🖼️</div>
                <h2>有什么可以帮你？</h2>
                <p>试试这些：</p>
                <div class="suggestions" id="suggestions"></div>
            </div>`;
        renderSuggestions();
    }

    /* ---------------- 消息渲染 ---------------- */
    function addMessage(role, text) {
        const empty = $('messages').querySelector('.empty-state');
        if (empty) empty.remove();

        const el = document.createElement('div');
        el.className = 'msg ' + role;
        el.innerHTML = `
            <div class="avatar">${role === 'user' ? '🙋' : '🤖'}</div>
            <div class="body">
                <div class="who">${role === 'user' ? '你' : (state.currentAgent || 'AI 助手')}</div>
                <div class="text"></div>
            </div>`;
        el.querySelector('.text').textContent = text;
        $('messages').appendChild(el);
        scrollToBottom();
        return el;
    }

    function addToolCard(toolName, args) {
        const wrap = document.createElement('div');
        wrap.className = 'msg assistant';
        wrap.innerHTML = `
            <div class="avatar">🤖</div>
            <div class="body">
                <div class="tool-card">
                    <div class="tc-head">
                        <span>🔧</span>
                        <span class="tc-name">${escapeHtml(toolName)}</span>
                        <span class="tc-state running">执行中</span>
                    </div>
                    <div class="tc-body"><code>${escapeHtml(JSON.stringify(args || {}))}</code></div>
                </div>
            </div>`;
        $('messages').appendChild(wrap);
        scrollToBottom();
        return wrap.querySelector('.tool-card');
    }

    function updateToolCard(card, success, summary) {
        const stateEl = card.querySelector('.tc-state');
        stateEl.className = 'tc-state ' + (success ? 'ok' : 'fail');
        stateEl.textContent = success ? '完成' : '失败';
        const body = card.querySelector('.tc-body');
        body.innerHTML = `<code>${escapeHtml(summary || '')}</code>`;
        card.classList.add('collapsed');
        card.querySelector('.tc-head').addEventListener('click', () => {
            card.classList.toggle('collapsed');
        });
    }

    function addHandoffLine(text) {
        const el = document.createElement('div');
        el.className = 'handoff-line';
        el.textContent = text;
        $('messages').appendChild(el);
        scrollToBottom();
    }

    function setAgentBadge(agentName) {
        const agent = state.agents.find(a => a.name === agentName);
        state.currentAgent = agent ? agent.displayName : agentName;
        const badge = $('agentBadge');
        badge.hidden = false;
        badge.textContent = '● ' + state.currentAgent;
    }

    function scrollToBottom() {
        const box = $('messages');
        box.scrollTop = box.scrollHeight;
    }

    /* ---------------- 发送 ---------------- */
    async function send() {
        if (state.streaming) return;
        const text = $('input').value.trim();
        if (!text && !state.attachments.length) return;

        state.streaming = true;
        $('sendBtn').disabled = true;
        $('composerTip').textContent = '';

        const attachments = state.attachments.slice();
        addMessage('user', text || '（发送了图片）');
        if (attachments.length) {
            const last = $('messages').lastElementChild;
            const bar = document.createElement('div');
            bar.className = 'attachments';
            bar.innerHTML = attachments
                .map(a => `<img src="${escapeAttr(a.url)}" alt="">`)
                .join('');
            last.querySelector('.body').appendChild(bar);
        }

        $('input').value = '';
        state.attachments = [];
        renderAttachments();
        autoResize();

        // 助手气泡（流式）
        const empty = $('messages').querySelector('.empty-state');
        if (empty) empty.remove();
        const assistantEl = document.createElement('div');
        assistantEl.className = 'msg assistant';
        assistantEl.innerHTML = `
            <div class="avatar">🤖</div>
            <div class="body">
                <div class="who">${escapeHtml(state.currentAgent || 'AI 助手')}</div>
                <div class="text cursor-blink"></div>
            </div>`;
        $('messages').appendChild(assistantEl);
        scrollToBottom();
        const textEl = assistantEl.querySelector('.text');
        const bodyEl = assistantEl.querySelector('.body');
        let buffer = '';

        try {
            await streamChat({
                sessionId: state.sessionId,
                message: text,
                attachmentIds: attachments.map(a => a.attachmentId)
            }, (evt) => {
                switch (evt.type) {
                    case 'agent_switch':
                        setAgentBadge(evt.agent);
                        assistantEl.querySelector('.who').textContent = evt.displayName || evt.agent;
                        if (evt.summary) addHandoffLine('转交给「' + evt.displayName + '」：' + evt.summary);
                        break;

                    case 'session':
                        state.sessionId = evt.sessionId;
                        break;

                    case 'tool_call': {
                        // 工具卡片插到助手气泡之前
                        const card = addToolCard(evt.tool, evt.args);
                        const holder = card.closest('.msg');
                        $('messages').insertBefore(holder, assistantEl);
                        assistantEl._lastCard = card;
                        break;
                    }

                    case 'tool_result':
                        if (assistantEl._lastCard) {
                            updateToolCard(assistantEl._lastCard, evt.success, evt.summary);
                            assistantEl._lastCard = null;
                        }
                        break;

                    case 'delta':
                        buffer += evt.content || '';
                        textEl.textContent = buffer;
                        scrollToBottom();
                        break;

                    case 'confirm_required':
                        state.pendingConfirm = {
                            token: evt.confirmToken,
                            tool: evt.tool,
                            args: evt.args,
                            desc: evt.summary
                        };
                        textEl.classList.remove('cursor-blink');
                        if (!buffer) {
                            textEl.textContent = '这一步需要你确认后才能继续。';
                        }
                        showConfirm(state.pendingConfirm);
                        break;

                    case 'done':
                        if (evt.messageId) assistantEl.dataset.messageId = evt.messageId;
                        textEl.classList.remove('cursor-blink');
                        loadSessions();
                        break;

                    case 'error':
                        textEl.classList.remove('cursor-blink');
                        const err = document.createElement('div');
                        err.className = 'error-bubble';
                        err.textContent = evt.message || '出错了';
                        bodyEl.appendChild(err);
                        break;
                }
            });
        } catch (e) {
            textEl.classList.remove('cursor-blink');
            const err = document.createElement('div');
            err.className = 'error-bubble';
            err.textContent = e.message;
            bodyEl.appendChild(err);
        } finally {
            textEl.classList.remove('cursor-blink');
            state.streaming = false;
            $('sendBtn').disabled = false;
            scrollToBottom();
        }
    }

    /* ---------------- SSE ---------------- */
    async function streamChat(payload, onEvent) {
        const resp = await fetch(BASE + '/agent/chat/stream', {
            method: 'POST',
            credentials: 'same-origin',
            headers: { 'Content-Type': 'application/json' },
            body: JSON.stringify(payload)
        });

        const ctype = resp.headers.get('content-type') || '';
        if (!ctype.includes('text/event-stream')) {
            // 出错时后端返回的是 JSON
            const text = await resp.text();
            let msg = '请求失败';
            try {
                const data = JSON.parse(text);
                msg = data.message || msg;
            } catch (e) { msg = text.slice(0, 200) || msg; }
            throw new Error(msg);
        }

        const reader = resp.body.getReader();
        const decoder = new TextDecoder('utf-8');
        let buffer = '';

        while (true) {
            const { done, value } = await reader.read();
            if (done) break;
            buffer += decoder.decode(value, { stream: true });
            buffer = buffer.replace(/\r\n/g, '\n');

            let idx;
            while ((idx = buffer.indexOf('\n\n')) >= 0) {
                const block = buffer.slice(0, idx);
                buffer = buffer.slice(idx + 2);
                const evt = parseEvent(block);
                if (evt) onEvent(evt);
            }
        }
    }

    function parseEvent(block) {
        const lines = block.split('\n');
        let data = '';
        for (const line of lines) {
            if (line.startsWith('data:')) {
                data += line.slice(5).trim();
            }
        }
        if (!data) return null;
        try {
            return JSON.parse(data);
        } catch (e) {
            return null;
        }
    }

    /* ---------------- 确认弹窗 ---------------- */
    function showConfirm(info) {
        $('confirmDesc').textContent = info.desc || ('即将执行 ' + info.tool);
        $('confirmArgs').textContent = JSON.stringify(info.args || {}, null, 2);
        $('confirmMask').hidden = false;
    }

    async function submitConfirm(approve) {
        const pending = state.pendingConfirm;
        $('confirmMask').hidden = true;
        if (!pending) return;

        state.streaming = true;
        $('sendBtn').disabled = true;
        try {
            await streamConfirm(pending.token, approve);
        } catch (e) {
            alert(e.message);
        } finally {
            // 确认过程中可能又触发了新的确认，只有令牌还是自己时才清空
            if (state.pendingConfirm && state.pendingConfirm.token === pending.token) {
                state.pendingConfirm = null;
            }
            state.streaming = false;
            $('sendBtn').disabled = false;
            loadSessions();
        }
    }

    async function streamConfirm(token, approve) {
        const resp = await fetch(BASE + '/agent/confirm/stream', {
            method: 'POST',
            credentials: 'same-origin',
            headers: { 'Content-Type': 'application/json' },
            body: JSON.stringify({ confirmToken: token, approve })
        });

        const ctype = resp.headers.get('content-type') || '';
        if (!ctype.includes('text/event-stream')) {
            const text = await resp.text();
            let msg = '操作失败';
            try { msg = JSON.parse(text).message || msg; } catch (e) { /* ignore */ }
            throw new Error(msg);
        }

        // 复用一条助手气泡来展示后续回答
        const assistantEl = document.createElement('div');
        assistantEl.className = 'msg assistant';
        assistantEl.innerHTML = `
            <div class="avatar">🤖</div>
            <div class="body">
                <div class="who">${escapeHtml(state.currentAgent || 'AI 助手')}</div>
                <div class="text cursor-blink"></div>
            </div>`;
        $('messages').appendChild(assistantEl);
        const textEl = assistantEl.querySelector('.text');
        const bodyEl = assistantEl.querySelector('.body');
        let buffer = '';

        const reader = resp.body.getReader();
        const decoder = new TextDecoder('utf-8');
        let raw = '';
        while (true) {
            const { done, value } = await reader.read();
            if (done) break;
            raw += decoder.decode(value, { stream: true });
            raw = raw.replace(/\r\n/g, '\n');
            let idx;
            while ((idx = raw.indexOf('\n\n')) >= 0) {
                const block = raw.slice(0, idx);
                raw = raw.slice(idx + 2);
                const evt = parseEvent(block);
                if (!evt) continue;

                if (evt.type === 'delta') {
                    buffer += evt.content || '';
                    textEl.textContent = buffer;
                    scrollToBottom();
                } else if (evt.type === 'tool_call') {
                    const card = addToolCard(evt.tool, evt.args);
                    $('messages').insertBefore(card.closest('.msg'), assistantEl);
                    assistantEl._lastCard = card;
                } else if (evt.type === 'tool_result') {
                    if (assistantEl._lastCard) {
                        updateToolCard(assistantEl._lastCard, evt.success, evt.summary);
                        assistantEl._lastCard = null;
                    }
                } else if (evt.type === 'agent_switch') {
                    setAgentBadge(evt.agent);
                } else if (evt.type === 'confirm_required') {
                    // 确认流程里又触发了新的高危操作
                    state.pendingConfirm = {
                        token: evt.confirmToken,
                        tool: evt.tool,
                        args: evt.args,
                        desc: evt.summary
                    };
                    showConfirm(state.pendingConfirm);
                } else if (evt.type === 'error') {
                    const err = document.createElement('div');
                    err.className = 'error-bubble';
                    err.textContent = evt.message || '出错了';
                    bodyEl.appendChild(err);
                }
            }
        }
        textEl.classList.remove('cursor-blink');
        scrollToBottom();
    }

    $('confirmApprove').addEventListener('click', () => submitConfirm(true));
    $('confirmReject').addEventListener('click', () => submitConfirm(false));

    /* ---------------- 附件 ---------------- */
    $('attachBtn').addEventListener('click', () => $('fileInput').click());

    $('fileInput').addEventListener('change', async (ev) => {
        const file = ev.target.files && ev.target.files[0];
        ev.target.value = '';
        if (!file) return;
        if (file.size > 10 * 1024 * 1024) {
            alert('文件不能超过 10MB');
            return;
        }
        $('composerTip').textContent = '上传中…';
        const form = new FormData();
        form.append('file', file);
        try {
            const data = await api('/agent/attachment/upload', { method: 'POST', body: form });
            state.attachments.push(data);
            renderAttachments();
            $('composerTip').textContent = '';
        } catch (e) {
            $('composerTip').textContent = '';
            alert('上传失败：' + e.message);
        }
    });

    function renderAttachments() {
        const bar = $('attachBar');
        if (!state.attachments.length) {
            bar.hidden = true;
            bar.innerHTML = '';
            return;
        }
        bar.hidden = false;
        bar.innerHTML = state.attachments.map((a, i) => `
            <span class="attach-chip">
                <img src="${escapeAttr(a.url)}" alt="">
                <span>${escapeHtml(a.fileName || '图片')}</span>
                <button data-i="${i}" title="移除">✕</button>
            </span>`).join('');
        bar.querySelectorAll('button').forEach(btn => {
            btn.addEventListener('click', () => {
                state.attachments.splice(Number(btn.dataset.i), 1);
                renderAttachments();
            });
        });
    }

    /* ---------------- 任务 ---------------- */
    async function loadTasks() {
        try {
            const list = await api('/agent/task/list');
            const box = $('taskList');
            if (!list || !list.length) {
                box.innerHTML = '<div style="font-size:12px;color:var(--text-3);padding:4px 8px">暂无任务</div>';
                return;
            }
            box.innerHTML = list.slice(0, 6).map(t => `
                <div class="task-item">
                    <div class="t-row">
                        <span class="t-id">${escapeHtml((t.taskId || '').slice(0, 8))}</span>
                        <span class="task-status ${t.status}">${statusText(t.status)}</span>
                    </div>
                    <div class="t-row" style="margin-top:4px">
                        <span>${escapeHtml(t.progressMessage || t.type || '')}</span>
                        <span>${t.progress || 0}%</span>
                    </div>
                    <div class="progress-bar"><i style="width:${t.progress || 0}%"></i></div>
                </div>`).join('');
        } catch (e) {
            /* 未登录等情况忽略 */
        }
    }

    function statusText(s) {
        return ({
            PENDING: '排队中', RUNNING: '执行中',
            SUCCESS: '完成', FAILED: '失败', CANCELED: '已取消'
        })[s] || s;
    }

    $('refreshTasksBtn').addEventListener('click', loadTasks);

    /* ---------------- 输入框 ---------------- */
    const input = $('input');

    function autoResize() {
        input.style.height = 'auto';
        input.style.height = Math.min(input.scrollHeight, 180) + 'px';
    }

    input.addEventListener('input', autoResize);
    input.addEventListener('keydown', (ev) => {
        if (ev.key === 'Enter' && !ev.shiftKey) {
            ev.preventDefault();
            send();
        }
    });
    $('sendBtn').addEventListener('click', send);

    /* ---------------- 工具函数 ---------------- */
    function escapeHtml(s) {
        return String(s == null ? '' : s)
            .replace(/&/g, '&amp;')
            .replace(/</g, '&lt;')
            .replace(/>/g, '&gt;')
            .replace(/"/g, '&quot;');
    }

    function escapeAttr(s) {
        return escapeHtml(s).replace(/'/g, '&#39;');
    }

    /* ---------------- 启动 ---------------- */
    (async function init() {
        if (await checkLogin()) {
            await boot();
        } else {
            $('loginMask').hidden = false;
            $('account').focus();
        }
    })();

})();
