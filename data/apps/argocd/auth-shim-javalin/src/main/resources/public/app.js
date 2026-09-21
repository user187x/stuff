let stack = [];

const baseVariables = [
    'header.authorization',
    'header.x-forwarded-user',
    'header.x-forwarded-host',
    'header.x-forwarded-uri',
    'payload.token'
];

async function loadData() {
    const res = await fetch('/api/config');
    stack = await res.json();
    render();
}

async function saveStack() {
    await fetch('/api/config', {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify(stack)
    });
    alert('Pipeline saved successfully!');
}

function addService() {
    stack.push({
        id: crypto.randomUUID(),
        name: 'New Service',
        url: 'http://',
        headers: [],
        payloadExtractions: [],
        responseExtractions: [],
        collapsed: false
    });
    render();
}

function toggleCollapse(index) {
    stack[index].collapsed = !stack[index].collapsed;
    render();
}

function removeService(index) {
    stack.splice(index, 1);
    render();
}

function updateField(index, field, value) {
    stack[index][field] = value;
    render();
}

function addArrItem(serviceIndex, arrName, defaultObj) {
    stack[serviceIndex][arrName].push(defaultObj);
    render();
}
function updateArrItem(serviceIndex, arrName, itemIndex, field, value) {
    stack[serviceIndex][arrName][itemIndex][field] = value;
    render();
}
function removeArrItem(serviceIndex, arrName, itemIndex) {
    stack[serviceIndex][arrName].splice(itemIndex, 1);
    render();
}

// --- Drag and Drop Handlers ---
function handleDragStart(e, index) {
    e.dataTransfer.setData('text/plain', index);
    e.dataTransfer.effectAllowed = 'move';
    e.target.classList.add('dragging');
}

function handleDragOver(e) {
    e.preventDefault();
    e.dataTransfer.dropEffect = 'move';
}

function handleDrop(e, targetIndex) {
    e.preventDefault();
    e.stopPropagation();
    const sourceIndex = parseInt(e.dataTransfer.getData('text/plain'), 10);

    if (sourceIndex !== targetIndex && !isNaN(sourceIndex)) {
        const [movedItem] = stack.splice(sourceIndex, 1);
        stack.splice(targetIndex, 0, movedItem);
        render();
    }
}

function handleDragEnd(e) {
    e.target.classList.remove('dragging');
    render();
}

function render() {
    const container = document.getElementById('pipeline-container');
    container.innerHTML = '';

    let availableVars = [...baseVariables];

    stack.forEach((service, index) => {
        const div = document.createElement('div');
        div.className = 'stack-item';
        // Make the entire block draggable
        div.draggable = true;
        div.ondragstart = (e) => handleDragStart(e, index);
        div.ondragover = handleDragOver;
        div.ondrop = (e) => handleDrop(e, index);
        div.ondragend = handleDragEnd;

        const isCollapsed = service.collapsed;

        // 1. Header (Double-click to collapse, visible drag grip icon)
        let html = `
            <div style="display: flex; justify-content: space-between; align-items: center; cursor: pointer; user-select: none; padding: 5px; background: ${isCollapsed ? '#f1f5f9' : 'transparent'}; border-radius: 4px; transition: background 0.2s;"
                 ondblclick="toggleCollapse(${index})"
                 title="Double click to ${isCollapsed ? 'expand' : 'collapse'}">

                <h3 style="margin: 5px 0; display: flex; align-items: center;">
                    <span class="drag-handle" title="Drag to reorder">☰</span>
                    <span style="display:inline-block; width: 20px; font-size: 0.8em; color: #666;">${isCollapsed ? '▶' : '▼'}</span>
                    ${index + 1}.
                    <input type="text" value="${service.name}"
                        onchange="updateField(${index}, 'name', this.value)"
                        onclick="event.stopPropagation()"
                        ondblclick="event.stopPropagation()"
                        style="font-weight: bold; font-size: 1.1em; border: none; border-bottom: 1px solid #ccc; background: transparent; width: 300px; margin-left: 10px;">
                </h3>

                <div>
                    <button class="danger" onclick="removeService(${index}); event.stopPropagation()">Delete</button>
                </div>
            </div>
        `;

        if (!isCollapsed) {
            html += `
                <div class="row" style="margin-top: 10px; margin-left: 55px;">
                    <label style="width: 100px;">Target URL:</label>
                    <input type="text" value="${service.url}" onchange="updateField(${index}, 'url', this.value)" style="width: 400px;">
                </div>
                <hr style="border: 0; border-top: 1px solid #eee; margin: 15px 0;">
                <h4>Incoming Extraction (Map payload/headers to Context Keys)</h4>
            `;
        }

        service.payloadExtractions.forEach((ext, pIndex) => {
            if (!isCollapsed) {
                html += `<div class="row">
                    <select onchange="updateArrItem(${index}, 'payloadExtractions', ${pIndex}, 'sourceKey', this.value)">
                        <option value="">-- Select Source --</option>
                        ${availableVars.map(v => `<option value="${v}" ${v === ext.sourceKey ? 'selected' : ''}>${v}</option>`).join('')}
                    </select>
                    <span>&rarr; Save As Key:</span>
                    <input type="text" value="${ext.saveToKey}" placeholder="e.g. context_token" onchange="updateArrItem(${index}, 'payloadExtractions', ${pIndex}, 'saveToKey', this.value)">
                    <button class="danger" onclick="removeArrItem(${index}, 'payloadExtractions', ${pIndex})">x</button>
                </div>`;
            }
        });

        if (!isCollapsed) {
            html += `<button class="secondary" onclick="addArrItem(${index}, 'payloadExtractions', {sourceKey: '', saveToKey: ''})">+ Map Incoming Var</button>`;
        }

        service.payloadExtractions.forEach(ext => { if(ext.saveToKey) availableVars.push(ext.saveToKey); });

        if (!isCollapsed) {
            html += `<h4>Headers to External Service</h4>`;
        }

        service.headers.forEach((hdr, hIndex) => {
            if (!isCollapsed) {
                html += `<div class="row">
                    <input type="text" value="${hdr.targetHeader}" placeholder="Target Header Name" onchange="updateArrItem(${index}, 'headers', ${hIndex}, 'targetHeader', this.value)">
                    <span> = </span>
                    <select onchange="updateArrItem(${index}, 'headers', ${hIndex}, 'valueSourceKey', this.value)">
                        <option value="">-- Select Value Source --</option>
                        ${availableVars.map(v => `<option value="${v}" ${v === hdr.valueSourceKey ? 'selected' : ''}>${v}</option>`).join('')}
                    </select>
                    <button class="danger" onclick="removeArrItem(${index}, 'headers', ${hIndex})">x</button>
                </div>`;
            }
        });

        if (!isCollapsed) {
            html += `<button class="secondary" onclick="addArrItem(${index}, 'headers', {targetHeader: '', valueSourceKey: ''})">+ Add Header</button>`;
            html += `<h4>External Response Extraction (JSON Path)</h4>`;
        }

        service.responseExtractions.forEach((ext, rIndex) => {
            if (!isCollapsed) {
                html += `<div class="row">
                    <input type="text" value="${ext.jsonPath}" placeholder="$.user.id" onchange="updateArrItem(${index}, 'responseExtractions', ${rIndex}, 'jsonPath', this.value)">
                    <span>&rarr; Save As Key:</span>
                    <input type="text" value="${ext.saveToKey}" placeholder="e.g. user_id" onchange="updateArrItem(${index}, 'responseExtractions', ${rIndex}, 'saveToKey', this.value)">
                    <button class="danger" onclick="removeArrItem(${index}, 'responseExtractions', ${rIndex})">x</button>
                </div>`;
            }
        });

        if (!isCollapsed) {
            html += `<button class="secondary" onclick="addArrItem(${index}, 'responseExtractions', {jsonPath: '$.', saveToKey: ''})">+ Extract from Response</button>`;
        }

        service.responseExtractions.forEach(ext => { if(ext.saveToKey) availableVars.push(ext.saveToKey); });

        div.innerHTML = html;
        container.appendChild(div);
    });
}

loadData();
