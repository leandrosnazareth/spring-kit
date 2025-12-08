const structureTypeOptions = [
    { value: 'CLASS', label: 'Classe' },
    { value: 'ABSTRACT_CLASS', label: 'Classe Abstrata' },
    { value: 'INTERFACE', label: 'Interface' },
    { value: 'ENUM', label: 'Enum' }
];

const crudFieldTypes = [
    { value: 'String', label: 'String' },
    { value: 'Long', label: 'Long' },
    { value: 'Integer', label: 'Integer' },
    { value: 'Double', label: 'Double' },
    { value: 'BigDecimal', label: 'BigDecimal' },
    { value: 'Boolean', label: 'Boolean' },
    { value: 'LocalDate', label: 'LocalDate' },
    { value: 'LocalDateTime', label: 'LocalDateTime' },
    { value: 'OBJECT', label: 'Objeto (Entity)' }
];

const relationshipTypes = [
    { value: 'ONE_TO_ONE', label: 'One To One' },
    { value: 'ONE_TO_MANY', label: 'One To Many' },
    { value: 'MANY_TO_ONE', label: 'Many To One' },
    { value: 'MANY_TO_MANY', label: 'Many To Many' }
];

const relationshipLabelMap = relationshipTypes.reduce((acc, rel) => {
    acc[rel.value] = rel.label;
    return acc;
}, {});

const canvasInteractiveSelector = 'input, select, textarea, button, [contenteditable="true"], .canvas-field-row, .method-row, .enum-row';

function isCanvasInteractiveElement(target) {
    if (!target) return false;
    return Boolean(target.closest(canvasInteractiveSelector));
}

const crudState = {
    classes: [],
    selectedClassId: null
};

let crudDragState = null;
let relationshipDragState = null;

function syncArtifactToName() {
    const artifactId = document.getElementById('artifactId')?.value || '';
    const nameInput = document.getElementById('name');
    if (artifactId && nameInput) {
        const nameParts = artifactId.split('-');
        const camelCaseName = nameParts
            .map(part => part.charAt(0).toUpperCase() + part.slice(1))
            .join('');
        nameInput.value = camelCaseName;
    }
    updatePackageName();
}

function updatePackageName() {
    const autoPackage = document.getElementById('autoPackage');
    if (!autoPackage || !autoPackage.checked) {
        return;
    }
    const groupId = document.getElementById('groupId')?.value || '';
    const artifactId = document.getElementById('artifactId')?.value || '';
    const packageNameInput = document.getElementById('packageName');
    if (!packageNameInput) {
        return;
    }
    if (groupId && artifactId) {
        const packageSuffix = artifactId.replace(/-/g, '');
        packageNameInput.value = `${groupId}.${packageSuffix}`;
    } else if (groupId) {
        packageNameInput.value = groupId;
    }
}

function disableAutoPackage() {
    const autoPackage = document.getElementById('autoPackage');
    const packageName = document.getElementById('packageName');
    if (!autoPackage || !packageName) return;
    const groupId = document.getElementById('groupId')?.value || '';
    const artifactId = document.getElementById('artifactId')?.value || '';
    if (groupId && artifactId) {
        const expectedValue = `${groupId}.${artifactId.replace(/-/g, '')}`;
        if (packageName.value !== expectedValue && packageName.value !== groupId) {
            autoPackage.checked = false;
        }
    }
}

function validateForm() {
    const form = document.querySelector('form');
    if (!form) return true;
    if (!form.checkValidity()) {
        form.reportValidity();
        return false;
    }
    const name = (document.querySelector('input[name="name"]')?.value || '').trim();
    const packageName = (document.querySelector('input[name="packageName"]')?.value || '').trim();
    const javaKeywords = ['abstract', 'assert', 'boolean', 'break', 'byte', 'case', 'catch', 'char',
        'class', 'const', 'continue', 'default', 'do', 'double', 'else', 'enum',
        'extends', 'final', 'finally', 'float', 'for', 'goto', 'if', 'implements',
        'import', 'instanceof', 'int', 'interface', 'long', 'native', 'new', 'package',
        'private', 'protected', 'public', 'return', 'short', 'static', 'strictfp',
        'super', 'switch', 'synchronized', 'this', 'throw', 'throws', 'transient',
        'try', 'void', 'volatile', 'while'];
    if (javaKeywords.includes(name.toLowerCase())) {
        alert(`Error: "${name}" is a Java reserved keyword. Please choose a different name.`);
        return false;
    }
    const packageParts = packageName.split('.');
    for (const part of packageParts) {
        if (javaKeywords.includes(part)) {
            alert(`Error: Package name contains Java reserved keyword "${part}". Please choose a different package name.`);
            return false;
        }
    }
    if (!prepareCrudDefinition()) {
        return false;
    }
    return true;
}

function filterDependencies() {
    const searchInput = document.getElementById('searchDeps')?.value.toLowerCase() || '';
    const dependencyItems = document.querySelectorAll('.dependency-item');
    dependencyItems.forEach(item => {
        const name = (item.querySelector('.dependency-name')?.textContent || '').toLowerCase();
        const desc = (item.querySelector('.dependency-desc')?.textContent || '').toLowerCase();
        item.style.display = (name.includes(searchInput) || desc.includes(searchInput)) ? '' : 'none';
    });
}

function updateSpringVersions() {
    const javaVersion = document.getElementById('javaVersion')?.value || '';
    const springVersionSelect = document.getElementById('springBootVersion');
    if (!springVersionSelect) return;
    fetch('/api/spring-versions?javaVersion=' + encodeURIComponent(javaVersion))
        .then(response => response.json())
        .then(versions => {
            springVersionSelect.innerHTML = '';
            versions.forEach(version => {
                const option = document.createElement('option');
                option.value = version;
                option.textContent = version;
                springVersionSelect.appendChild(option);
            });
        })
        .catch(() => {
            // se falhar mantém opções anteriores
        });
}

function resetForm() {
    const form = document.querySelector('form');
    if (!form) return;
    form.reset();
    const auto = document.getElementById('autoPackage');
    if (auto) auto.checked = true;
    updateSpringVersions();
    resetCrudBuilder();
}

function moveToTop(item) {
    const grid = item.parentElement;
    const firstItem = grid.firstElementChild;
    if (!item.dataset.originalIndex) {
        const items = Array.from(grid.children);
        item.dataset.originalIndex = items.indexOf(item);
    }
    grid.insertBefore(item, firstItem);
}

function restoreOriginalPosition(item) {
    const grid = item.parentElement;
    const originalIndex = parseInt(item.dataset.originalIndex, 10);
    if (isNaN(originalIndex)) return;
    const items = Array.from(grid.children);
    let insertBeforeElement = null;
    for (let i = 0; i < items.length; i++) {
        const currentItem = items[i];
        const currentOriginalIndex = parseInt(currentItem.dataset.originalIndex, 10);
        if (!isNaN(currentOriginalIndex) && currentOriginalIndex > originalIndex) {
            insertBeforeElement = currentItem;
            break;
        }
    }
    if (insertBeforeElement) {
        grid.insertBefore(item, insertBeforeElement);
    } else {
        grid.appendChild(item);
    }
}

function updateDependencyCounter() {
    const checkedCount = document.querySelectorAll('.dependency-item input[type="checkbox"]:checked').length;
    const el = document.getElementById('depCounter');
    if (el) el.textContent = `(${checkedCount} selected)`;
}

function prepareCrudDefinition() {
    const hidden = document.getElementById('crudDefinition');
    if (!hidden) return true;
    if (!crudState.classes.length) {
        hidden.value = '';
        return true;
    }
    const payload = buildCrudPayload();
    if (!payload) {
        return false;
    }
    hidden.value = JSON.stringify(payload);
    return true;
}

function initCrudBuilder() {
    document.getElementById('addCrudClassBtn')?.addEventListener('click', addCrudClass);

    renderCrudCanvas();
}

function resetCrudBuilder() {
    crudState.classes = [];
    crudState.selectedClassId = null;
    const hidden = document.getElementById('crudDefinition');
    if (hidden) hidden.value = '';
    renderCrudCanvas();
}

function addCrudClass() {
    const id = crudRandomId('cls');
    const newClass = {
        id,
        name: `Class${crudState.classes.length + 1}`,
        tableName: '',
        structureType: 'CLASS',
        collapsed: false,
        x: 40 + crudState.classes.length * 25,
        y: 40 + crudState.classes.length * 25,
        fields: [
            {
                id: crudRandomId('fld'),
                name: 'id',
                type: 'Long',
                identifier: true,
                required: true,
                unique: true,
                objectType: false,
                targetClassId: null,
                relationshipType: null
            }
        ],
        methods: [],
        enumConstants: []
    };
    crudState.classes.push(newClass);
    crudState.selectedClassId = newClass.id;
    renderCrudCanvas();
}

function selectCrudClass(classId) {
    crudState.selectedClassId = classId;
    renderCrudCanvas();
    highlightSelectedNode();
}

function removeCrudClass(classId) {
    crudState.classes = crudState.classes.filter(cls => cls.id !== classId);
    if (crudState.selectedClassId === classId) {
        crudState.selectedClassId = crudState.classes[0]?.id || null;
    }
    crudState.classes.forEach(cls => {
        cls.fields.forEach(field => {
            if (field.targetClassId === classId) {
                field.objectType = false;
                field.targetClassId = null;
                field.relationshipType = null;
            }
        });
    });
    renderCrudCanvas();
}

function addAttributeToClass(classId) {
    const clazz = crudState.classes.find(cls => cls.id === classId);
    if (!clazz) return;
    ensureStructureFields(clazz);
    const isEntity = (clazz.structureType || 'CLASS') === 'CLASS';
    const newField = {
        id: crudRandomId('fld'),
        name: `field${clazz.fields.length}`,
        type: 'String',
        identifier: false,
        required: false,
        unique: false,
        objectType: false,
        targetClassId: null,
        relationshipType: isEntity ? relationshipTypes[0].value : null
    };
    clazz.fields.push(newField);
    renderCrudCanvas();
}

function removeAttributeFromClass(classId, fieldId) {
    const clazz = crudState.classes.find(cls => cls.id === classId);
    if (!clazz) return;
    if (clazz.fields.length === 1) {
        alert('A classe precisa ter ao menos um atributo.');
        return;
    }
    clazz.fields = clazz.fields.filter(field => field.id !== fieldId);
    const isEntity = (clazz.structureType || 'CLASS') === 'CLASS';
    if (isEntity && !clazz.fields.some(field => field.identifier)) {
        clazz.fields[0].identifier = true;
    }
    renderCrudCanvas();
}

function ensureStructureFields(clazz) {
    if (!clazz.structureType) {
        clazz.structureType = 'CLASS';
    }
    if (!Array.isArray(clazz.methods)) {
        clazz.methods = [];
    }
    if (!Array.isArray(clazz.enumConstants)) {
        clazz.enumConstants = [];
    }
    if (typeof clazz.collapsed !== 'boolean') {
        clazz.collapsed = false;
    }
}

function handleStructureTypeChange(clazz, newType) {
    const previousType = clazz.structureType || 'CLASS';
    clazz.structureType = newType;
    if (newType !== 'CLASS') {
        clazz.tableName = '';
        clazz.fields.forEach(field => {
            field.identifier = false;
            field.objectType = false;
            field.targetClassId = null;
            field.relationshipType = null;
        });
    } else if (previousType !== 'CLASS' && clazz.fields.length) {
        clazz.fields[0].identifier = true;
    }
    if (newType !== 'ENUM') {
        clazz.enumConstants = [];
    }
    renderCrudCanvas();
}

function addMethodToClass(classId) {
    const clazz = crudState.classes.find(cls => cls.id === classId);
    if (!clazz) return;
    ensureStructureFields(clazz);
    clazz.methods.push({
        id: crudRandomId('mtd'),
        name: `metodo${clazz.methods.length + 1}`,
        returnType: 'void',
        parameters: [],
        abstractMethod: false,
        defaultImplementation: false,
        body: ''
    });
    renderCrudCanvas();
}

function removeMethodFromClass(classId, methodId) {
    const clazz = crudState.classes.find(cls => cls.id === classId);
    if (!clazz || !Array.isArray(clazz.methods)) return;
    clazz.methods = clazz.methods.filter(method => method.id !== methodId);
    renderCrudCanvas();
}

function addParameterToMethod(classId, methodId) {
    const clazz = crudState.classes.find(cls => cls.id === classId);
    const method = clazz?.methods?.find(m => m.id === methodId);
    if (!method) return;
    if (!Array.isArray(method.parameters)) {
        method.parameters = [];
    }
    method.parameters.push({
        id: crudRandomId('prm'),
        name: `param${method.parameters.length + 1}`,
        type: 'String'
    });
    renderCrudCanvas();
}

function removeParameterFromMethod(classId, methodId, paramId) {
    const clazz = crudState.classes.find(cls => cls.id === classId);
    const method = clazz?.methods?.find(m => m.id === methodId);
    if (!method || !Array.isArray(method.parameters)) return;
    method.parameters = method.parameters.filter(param => param.id !== paramId);
    renderCrudCanvas();
}

function renderMethodRow(clazz, method) {
    const structureType = clazz.structureType || 'CLASS';
    const isInterface = structureType === 'INTERFACE';
    const row = document.createElement('div');
    row.className = 'method-row';
    row.addEventListener('mousedown', e => e.stopPropagation());

    const topRow = document.createElement('div');
    topRow.className = 'method-row-row';
    const nameGroup = document.createElement('div');
    nameGroup.className = 'form-group';
    const nameLabel = document.createElement('label');
    nameLabel.textContent = 'Nome';
    const nameInput = document.createElement('input');
    nameInput.type = 'text';
    nameInput.value = method.name || '';
    nameInput.addEventListener('input', e => {
        method.name = toCamelCase(e.target.value);
        e.target.value = method.name;
    });
    nameGroup.appendChild(nameLabel);
    nameGroup.appendChild(nameInput);

    const returnGroup = document.createElement('div');
    returnGroup.className = 'form-group';
    const returnLabel = document.createElement('label');
    returnLabel.textContent = 'Retorno';
    const returnInput = document.createElement('input');
    returnInput.type = 'text';
    returnInput.value = method.returnType || 'void';
    returnInput.addEventListener('input', e => {
        method.returnType = e.target.value || 'void';
    });
    returnGroup.appendChild(returnLabel);
    returnGroup.appendChild(returnInput);

    topRow.appendChild(nameGroup);
    topRow.appendChild(returnGroup);
    row.appendChild(topRow);

    const paramsLabel = document.createElement('label');
    paramsLabel.textContent = 'Parâmetros';
    row.appendChild(paramsLabel);
    const paramsContainer = document.createElement('div');
    paramsContainer.className = 'method-params';
    (method.parameters || []).forEach(parameter => {
        paramsContainer.appendChild(renderParameterRow(clazz, method, parameter));
    });
    const addParamBtn = document.createElement('button');
    addParamBtn.type = 'button';
    addParamBtn.className = 'btn btn-secondary btn-compact';
    addParamBtn.textContent = 'Adicionar parâmetro';
    addParamBtn.addEventListener('click', () => {
        addParameterToMethod(clazz.id, method.id);
    });
    paramsContainer.appendChild(addParamBtn);
    row.appendChild(paramsContainer);

    const controls = document.createElement('div');
    controls.className = 'method-controls';
    if (isInterface) {
        controls.appendChild(createCheckbox('Default', Boolean(method.defaultImplementation), checked => {
            method.defaultImplementation = checked;
        }));
    } else {
        controls.appendChild(createCheckbox('Abstrato', Boolean(method.abstractMethod), checked => {
            method.abstractMethod = checked;
        }));
    }
    row.appendChild(controls);

    const bodyLabel = document.createElement('label');
    bodyLabel.textContent = 'Corpo (opcional)';
    const bodyInput = document.createElement('textarea');
    bodyInput.rows = 3;
    bodyInput.value = method.body || '';
    bodyInput.addEventListener('input', e => {
        method.body = e.target.value;
    });
    row.appendChild(bodyLabel);
    row.appendChild(bodyInput);

    const actions = document.createElement('div');
    actions.className = 'row-actions';
    const removeBtn = document.createElement('button');
    removeBtn.type = 'button';
    removeBtn.className = 'btn btn-secondary btn-compact';
    removeBtn.textContent = 'Remover método';
    removeBtn.addEventListener('click', () => {
        removeMethodFromClass(clazz.id, method.id);
    });
    actions.appendChild(removeBtn);
    row.appendChild(actions);

    return row;
}

function renderParameterRow(clazz, method, parameter) {
    const row = document.createElement('div');
    row.className = 'parameter-row';
    row.addEventListener('mousedown', e => e.stopPropagation());
    const typeInput = document.createElement('input');
    typeInput.type = 'text';
    typeInput.placeholder = 'Tipo';
    typeInput.value = parameter.type || 'String';
    typeInput.addEventListener('input', e => {
        parameter.type = e.target.value || 'String';
    });
    const nameInput = document.createElement('input');
    nameInput.type = 'text';
    nameInput.placeholder = 'nome';
    nameInput.value = parameter.name || '';
    nameInput.addEventListener('input', e => {
        parameter.name = toCamelCase(e.target.value);
        e.target.value = parameter.name;
    });
    const removeBtn = document.createElement('button');
    removeBtn.type = 'button';
    removeBtn.className = 'btn btn-secondary btn-compact';
    removeBtn.textContent = 'Remover';
    removeBtn.addEventListener('click', () => {
        removeParameterFromMethod(clazz.id, method.id, parameter.id);
    });
    row.appendChild(typeInput);
    row.appendChild(nameInput);
    row.appendChild(removeBtn);
    return row;
}

function createCanvasFieldRow(clazz, field) {
    const structureType = clazz.structureType || 'CLASS';
    const isEntity = structureType === 'CLASS';
    const row = document.createElement('li');
    row.className = 'canvas-field-row';
    if (field.identifier) {
        row.classList.add('is-id');
    }
    row.addEventListener('mousedown', e => e.stopPropagation());

    const nameInput = document.createElement('input');
    nameInput.type = 'text';
    nameInput.className = 'canvas-field-name';
    nameInput.placeholder = 'nomeCampo';
    nameInput.value = field.name || '';
    nameInput.addEventListener('input', e => {
        field.name = toCamelCase(e.target.value);
        e.target.value = field.name;
    });

    const typeSelect = document.createElement('select');
    typeSelect.className = 'canvas-field-type';
    crudFieldTypes.forEach(type => {
        const option = document.createElement('option');
        option.value = type.value;
        option.textContent = type.label;
        if ((field.objectType && type.value === 'OBJECT') || (!field.objectType && type.value === field.type)) {
            option.selected = true;
        }
        typeSelect.appendChild(option);
    });
    typeSelect.addEventListener('change', e => {
        const value = e.target.value;
        if (value === 'OBJECT') {
            field.objectType = true;
            field.type = 'OBJECT';
            field.relationshipType = field.relationshipType || relationshipTypes[0].value;
            if (!field.targetClassId && crudState.classes.length) {
                const target = crudState.classes.find(cls => (cls.structureType || 'CLASS') === 'CLASS');
                field.targetClassId = target ? target.id : null;
            }
        } else {
            field.objectType = false;
            field.targetClassId = null;
            field.relationshipType = null;
            field.type = value;
        }
        renderCrudCanvas();
    });

    row.appendChild(nameInput);
    row.appendChild(typeSelect);

    if (field.objectType) {
        const targetSelect = document.createElement('select');
        targetSelect.className = 'canvas-field-target';
        crudState.classes.forEach(targetClass => {
            if ((targetClass.structureType || 'CLASS') !== 'CLASS') return;
            const option = document.createElement('option');
            option.value = targetClass.id;
            option.textContent = targetClass.name;
            if (targetClass.id === field.targetClassId) {
                option.selected = true;
            }
            targetSelect.appendChild(option);
        });
        targetSelect.addEventListener('change', e => {
            field.targetClassId = e.target.value;
            renderCrudCanvas();
        });

        const relationshipSelect = document.createElement('select');
        relationshipSelect.className = 'canvas-field-relationship';
        relationshipTypes.forEach(rel => {
            const option = document.createElement('option');
            option.value = rel.value;
            option.textContent = rel.label;
            if (rel.value === field.relationshipType) {
                option.selected = true;
            }
            relationshipSelect.appendChild(option);
        });
        relationshipSelect.addEventListener('change', e => {
            field.relationshipType = e.target.value;
            renderCrudCanvas();
        });

        row.appendChild(targetSelect);
        row.appendChild(relationshipSelect);
    }

    if (isEntity && !field.objectType) {
        const flags = document.createElement('div');
        flags.className = 'canvas-field-flags';
        const idCheckbox = createCheckbox('ID', field.identifier, checked => {
            if (!checked) {
                field.identifier = false;
                if (!clazz.fields.some(f => f.identifier)) {
                    field.identifier = true;
                }
            } else {
                clazz.fields.forEach(f => { f.identifier = f.id === field.id; });
            }
            renderCrudCanvas();
        });
        idCheckbox.classList.add('canvas-checkbox');
        idCheckbox.addEventListener('mousedown', e => e.stopPropagation());
        flags.appendChild(idCheckbox);
        const requiredCheckbox = createCheckbox('Req.', field.required, checked => {
            field.required = checked;
        });
        requiredCheckbox.classList.add('canvas-checkbox');
        requiredCheckbox.addEventListener('mousedown', e => e.stopPropagation());
        flags.appendChild(requiredCheckbox);
        const uniqueCheckbox = createCheckbox('Único', field.unique, checked => {
            field.unique = checked;
        });
        uniqueCheckbox.classList.add('canvas-checkbox');
        uniqueCheckbox.addEventListener('mousedown', e => e.stopPropagation());
        flags.appendChild(uniqueCheckbox);
        row.appendChild(flags);
    }

    const actions = document.createElement('div');
    actions.className = 'canvas-field-actions';
    const removeBtn = document.createElement('button');
    removeBtn.type = 'button';
    removeBtn.className = 'btn btn-secondary btn-compact';
    removeBtn.textContent = 'Excluir';
    removeBtn.addEventListener('click', e => {
        e.stopPropagation();
        removeAttributeFromClass(clazz.id, field.id);
    });
    actions.appendChild(removeBtn);
    row.appendChild(actions);
    return row;
}

function createCanvasEnumSection(clazz) {
    const section = document.createElement('div');
    section.className = 'uml-node-section';
    const header = document.createElement('div');
    header.className = 'section-header';
    const title = document.createElement('h4');
    title.textContent = 'Constantes do Enum';
    const addBtn = document.createElement('button');
    addBtn.type = 'button';
    addBtn.className = 'btn btn-secondary btn-compact';
    addBtn.textContent = 'Adicionar';
    addBtn.addEventListener('click', e => {
        e.stopPropagation();
        clazz.enumConstants.push('NOVO_VALOR');
        renderCrudCanvas();
    });
    header.appendChild(title);
    header.appendChild(addBtn);
    section.appendChild(header);
    const list = document.createElement('div');
    list.className = 'enum-list';
    if (!clazz.enumConstants.length) {
        const empty = document.createElement('p');
        empty.className = 'wizard-hint';
        empty.textContent = 'Nenhuma constante definida.';
        list.appendChild(empty);
    } else {
        clazz.enumConstants.forEach((constant, index) => {
            const row = document.createElement('div');
            row.className = 'enum-row';
            row.addEventListener('mousedown', e => e.stopPropagation());
            const input = document.createElement('input');
            input.type = 'text';
            input.value = constant || '';
            input.placeholder = 'VALOR';
            input.addEventListener('input', e => {
                clazz.enumConstants[index] = toConstantCase(e.target.value);
                e.target.value = clazz.enumConstants[index];
            });
            const removeBtn = document.createElement('button');
            removeBtn.type = 'button';
            removeBtn.className = 'btn btn-secondary btn-compact';
            removeBtn.textContent = 'Remover';
            removeBtn.addEventListener('click', () => {
                clazz.enumConstants.splice(index, 1);
                renderCrudCanvas();
            });
            row.appendChild(input);
            row.appendChild(removeBtn);
            list.appendChild(row);
        });
    }
    section.appendChild(list);
    return section;
}

function createCheckbox(labelText, checked, onChange) {
    const label = document.createElement('label');
    const checkbox = document.createElement('input');
    checkbox.type = 'checkbox';
    checkbox.checked = checked;
    checkbox.addEventListener('change', e => onChange(e.target.checked));
    label.appendChild(checkbox);
    const text = document.createElement('span');
    text.textContent = labelText;
    label.appendChild(text);
    return label;
}

function getClassNameById(id) {
    const cls = crudState.classes.find(c => c.id === id);
    return cls ? cls.name : null;
}

function describeField(field) {
    if (!field.objectType) {
        return `${field.name || 'campo'} : ${field.type}`;
    }
    const targetName = getClassNameById(field.targetClassId) || 'Objeto';
    const label = relationshipLabelMap[field.relationshipType] || field.relationshipType || '';
    const suffix = (field.relationshipType === 'ONE_TO_MANY' || field.relationshipType === 'MANY_TO_MANY') ? '[]' : '';
    return `${field.name || 'campo'} : ${targetName}${suffix}${label ? ' (' + label + ')' : ''}`;
}

function getStructureLabel(type) {
    const option = structureTypeOptions.find(opt => opt.value === type);
    return option ? option.label : '';
}

function ensureRelationshipLayer(canvas) {
    let svg = document.getElementById('relationshipLayer');
    if (svg) {
        svg.remove();
    }
    svg = document.createElementNS('http://www.w3.org/2000/svg', 'svg');
    svg.id = 'relationshipLayer';
    svg.classList.add('uml-connection-layer');
    canvas.appendChild(svg);
    return svg;
}

function renderCrudCanvas() {
    const canvas = document.getElementById('umlCanvas');
    if (!canvas) return;
    canvas.innerHTML = '';
    ensureRelationshipLayer(canvas);
    crudState.classes.forEach(clazz => {
        ensureStructureFields(clazz);
        const node = document.createElement('div');
        node.className = 'uml-node';
        if (clazz.id === crudState.selectedClassId) {
            node.classList.add('selected');
        }
        if (clazz.collapsed) {
            node.classList.add('collapsed');
        }
        node.dataset.id = clazz.id;
        node.style.left = `${clazz.x || 40}px`;
        node.style.top = `${clazz.y || 40}px`;

        const header = document.createElement('div');
        header.className = 'uml-node-header';
        const structureType = clazz.structureType || 'CLASS';
        const headerContent = document.createElement('div');
        headerContent.className = 'uml-header-content';
        const headerTitle = document.createElement('span');
        headerTitle.className = 'uml-node-title';
        headerTitle.contentEditable = true;
        headerTitle.spellcheck = false;
        headerTitle.textContent = clazz.name || 'Classe';
        headerTitle.addEventListener('mousedown', e => e.stopPropagation());
        headerTitle.addEventListener('keydown', e => {
            if (e.key === 'Enter') {
                e.preventDefault();
                headerTitle.blur();
            }
        });
        headerTitle.addEventListener('blur', () => {
            const newName = toPascalCase(headerTitle.textContent);
            if (!newName) {
                headerTitle.textContent = clazz.name || 'Classe';
                return;
            }
            if (newName !== clazz.name) {
                clazz.name = newName;
                renderRelationships();
            }
            headerTitle.textContent = clazz.name;
        });
        headerContent.appendChild(headerTitle);
        const typeLabel = getStructureLabel(structureType);
        if (typeLabel) {
            const badge = document.createElement('span');
            badge.className = 'uml-node-badge';
            badge.textContent = typeLabel;
            headerContent.appendChild(badge);
        }
        header.appendChild(headerContent);
        const headerActions = document.createElement('div');
        headerActions.className = 'uml-header-actions';
        const collapseBtn = document.createElement('button');
        collapseBtn.type = 'button';
        collapseBtn.className = 'uml-collapse-btn';
        collapseBtn.title = clazz.collapsed ? 'Expandir classe' : 'Minimizar classe';
        collapseBtn.innerHTML = `<i class="fa-solid ${clazz.collapsed ? 'fa-chevron-down' : 'fa-chevron-up'}"></i>`;
        collapseBtn.addEventListener('click', e => {
            e.stopPropagation();
            clazz.collapsed = !clazz.collapsed;
            renderCrudCanvas();
        });
        headerActions.appendChild(collapseBtn);
        let linkHandle = null;
        if (structureType === 'CLASS') {
            linkHandle = document.createElement('button');
            linkHandle.type = 'button';
            linkHandle.className = 'uml-link-handle';
            linkHandle.title = 'Criar relacionamento';
            linkHandle.addEventListener('mousedown', e => beginRelationshipDrag(e, clazz.id));
            headerActions.appendChild(linkHandle);
        }
        header.appendChild(headerActions);
        header.addEventListener('mousedown', e => {
            if (e.target.closest('.uml-collapse-btn') || e.target.closest('.uml-link-handle')) {
                return;
            }
            beginClassDrag(e, clazz.id);
        });
        node.appendChild(header);

        const nodeBody = document.createElement('div');
        nodeBody.className = 'uml-node-body';

        nodeBody.appendChild(buildStructureControls(clazz));

        const fieldSection = document.createElement('div');
        fieldSection.className = 'uml-node-section';
        const fieldHeader = document.createElement('div');
        fieldHeader.className = 'section-header';
        const fieldTitle = document.createElement('h4');
        fieldTitle.textContent = 'Atributos';
        const addFieldBtn = document.createElement('button');
        addFieldBtn.type = 'button';
        addFieldBtn.className = 'btn btn-secondary btn-compact';
        addFieldBtn.textContent = 'Adicionar';
        addFieldBtn.addEventListener('click', e => {
            e.stopPropagation();
            addAttributeToClass(clazz.id);
        });
        fieldHeader.appendChild(fieldTitle);
        fieldHeader.appendChild(addFieldBtn);
        fieldSection.appendChild(fieldHeader);
        const fieldList = document.createElement('ul');
        fieldList.className = 'uml-node-fields';
        if (!clazz.fields.length) {
            const empty = document.createElement('li');
            empty.className = 'canvas-field-row';
            empty.textContent = 'Nenhum atributo definido.';
            fieldList.appendChild(empty);
        } else {
            clazz.fields.forEach(field => fieldList.appendChild(createCanvasFieldRow(clazz, field)));
        }
        fieldSection.appendChild(fieldList);
        nodeBody.appendChild(fieldSection);

        if ((clazz.structureType || 'CLASS') === 'ENUM') {
            nodeBody.appendChild(createCanvasEnumSection(clazz));
        }

        nodeBody.appendChild(createMethodsSection(clazz));

        const actions = document.createElement('div');
        actions.className = 'uml-node-actions';
        const deleteBtn = document.createElement('button');
        deleteBtn.type = 'button';
        deleteBtn.className = 'btn btn-secondary btn-compact';
        deleteBtn.textContent = 'Excluir classe';
        deleteBtn.addEventListener('click', e => {
            e.stopPropagation();
            removeCrudClass(clazz.id);
        });
        actions.appendChild(deleteBtn);
        nodeBody.appendChild(actions);
        node.appendChild(nodeBody);

        node.addEventListener('click', e => {
            if (isCanvasInteractiveElement(e.target)) {
                return;
            }
            selectCrudClass(clazz.id);
        });
        canvas.appendChild(node);
    });
    highlightSelectedNode();
    updateCanvasVisibility();
    renderRelationships();
}

function buildStructureControls(clazz) {
    const wrapper = document.createElement('div');
    wrapper.className = 'uml-node-meta';
    const typeGroup = document.createElement('div');
    typeGroup.className = 'uml-control-group';
    const typeLabel = document.createElement('label');
    typeLabel.textContent = 'Tipo';
    const typeSelect = document.createElement('select');
    structureTypeOptions.forEach(opt => {
        const option = document.createElement('option');
        option.value = opt.value;
        option.textContent = opt.label;
        if (opt.value === (clazz.structureType || 'CLASS')) {
            option.selected = true;
        }
        typeSelect.appendChild(option);
    });
    typeSelect.addEventListener('change', e => {
        handleStructureTypeChange(clazz, e.target.value);
    });
    typeGroup.appendChild(typeLabel);
    typeGroup.appendChild(typeSelect);
    wrapper.appendChild(typeGroup);
    if ((clazz.structureType || 'CLASS') === 'CLASS') {
        const tableGroup = document.createElement('div');
        tableGroup.className = 'uml-control-group';
        const tableLabel = document.createElement('label');
        tableLabel.textContent = 'Tabela';
        const tableInput = document.createElement('input');
        tableInput.type = 'text';
        tableInput.placeholder = 'ex: produtos';
        tableInput.value = clazz.tableName || '';
        tableInput.addEventListener('input', e => {
            clazz.tableName = e.target.value;
        });
        tableGroup.appendChild(tableLabel);
        tableGroup.appendChild(tableInput);
        wrapper.appendChild(tableGroup);
    }
    return wrapper;
}

function createMethodsSection(clazz) {
    const section = document.createElement('div');
    section.className = 'uml-node-section';
    const header = document.createElement('div');
    header.className = 'section-header';
    const title = document.createElement('h4');
    title.textContent = 'Métodos';
    const addBtn = document.createElement('button');
    addBtn.type = 'button';
    addBtn.className = 'btn btn-secondary btn-compact';
    addBtn.textContent = 'Adicionar';
    addBtn.addEventListener('click', e => {
        e.stopPropagation();
        addMethodToClass(clazz.id);
    });
    header.appendChild(title);
    header.appendChild(addBtn);
    section.appendChild(header);
    const list = document.createElement('div');
    list.className = 'methods-list';
    if (!clazz.methods.length) {
        const empty = document.createElement('p');
        empty.className = 'wizard-hint';
        empty.textContent = 'Nenhum método definido.';
        list.appendChild(empty);
    } else {
        clazz.methods.forEach(method => list.appendChild(renderMethodRow(clazz, method)));
    }
    section.appendChild(list);
    return section;
}


function collectRelationships() {
    const relations = [];
    crudState.classes.forEach(clazz => {
        if ((clazz.structureType || 'CLASS') !== 'CLASS') {
            return;
        }
        clazz.fields.forEach(field => {
            if (field.objectType && field.targetClassId) {
                const target = crudState.classes.find(c => c.id === field.targetClassId);
                if (!target || (target.structureType || 'CLASS') !== 'CLASS') {
                    return;
                }
                relations.push({
                    sourceId: clazz.id,
                    targetId: field.targetClassId,
                    type: field.relationshipType || ''
                });
            }
        });
    });
    return relations;
}

function renderRelationships() {
    const canvas = document.getElementById('umlCanvas');
    const svg = document.getElementById('relationshipLayer');
    if (!canvas || !svg) return;
    const width = canvas.clientWidth;
    const height = canvas.clientHeight;
    svg.setAttribute('width', width);
    svg.setAttribute('height', height);
    svg.innerHTML = '';
    addArrowMarker(svg);
    const canvasRect = canvas.getBoundingClientRect();
    const relations = collectRelationships();
    relations.forEach(rel => {
        const sourceNode = canvas.querySelector(`.uml-node[data-id="${rel.sourceId}"]`);
        const targetNode = canvas.querySelector(`.uml-node[data-id="${rel.targetId}"]`);
        if (!sourceNode || !targetNode) return;
        const sourceCenter = getNodeCenter(sourceNode, canvasRect);
        const targetCenter = getNodeCenter(targetNode, canvasRect);

        const line = document.createElementNS('http://www.w3.org/2000/svg', 'line');
        line.setAttribute('class', 'relationship-line');
        line.setAttribute('x1', sourceCenter.x);
        line.setAttribute('y1', sourceCenter.y);
        line.setAttribute('x2', targetCenter.x);
        line.setAttribute('y2', targetCenter.y);
        line.setAttribute('marker-end', 'url(#uml-arrowhead)');
        svg.appendChild(line);

        const label = document.createElementNS('http://www.w3.org/2000/svg', 'text');
        label.setAttribute('class', 'relationship-label');
        const midX = (sourceCenter.x + targetCenter.x) / 2;
        const midY = (sourceCenter.y + targetCenter.y) / 2;
        label.setAttribute('x', midX);
        label.setAttribute('y', midY - 4);
        label.textContent = relationshipLabelMap[rel.type] || rel.type;
        svg.appendChild(label);
    });
}

function addArrowMarker(svg) {
    const defs = document.createElementNS('http://www.w3.org/2000/svg', 'defs');
    const marker = document.createElementNS('http://www.w3.org/2000/svg', 'marker');
    marker.setAttribute('id', 'uml-arrowhead');
    marker.setAttribute('markerWidth', '10');
    marker.setAttribute('markerHeight', '7');
    marker.setAttribute('refX', '10');
    marker.setAttribute('refY', '3.5');
    marker.setAttribute('orient', 'auto');
    const path = document.createElementNS('http://www.w3.org/2000/svg', 'path');
    path.setAttribute('d', 'M0,0 L10,3.5 L0,7 Z');
    path.setAttribute('fill', '#1abc9c');
    marker.appendChild(path);
    defs.appendChild(marker);
    svg.appendChild(defs);
}

function getNodeCenter(node, canvasRect) {
    const rect = node.getBoundingClientRect();
    return {
        x: rect.left - canvasRect.left + rect.width / 2,
        y: rect.top - canvasRect.top + rect.height / 2
    };
}

function highlightSelectedNode() {
    const nodes = document.querySelectorAll('.uml-node');
    nodes.forEach(node => {
        if (node.dataset.id === crudState.selectedClassId) {
            node.classList.add('selected');
        } else {
            node.classList.remove('selected');
        }
    });
}

function updateCanvasVisibility() {
    const canvas = document.getElementById('umlCanvas');
    const emptyState = document.getElementById('crudCanvasEmpty');
    if (!canvas || !emptyState) return;
    if (crudState.classes.length === 0) {
        emptyState.classList.remove('hidden');
        canvas.style.display = 'none';
    } else {
        emptyState.classList.add('hidden');
        canvas.style.display = 'block';
    }
}

function beginClassDrag(event, classId) {
    event.preventDefault();
    const clazz = crudState.classes.find(cls => cls.id === classId);
    if (!clazz) return;
    crudDragState = {
        classId,
        offsetX: event.clientX - (clazz.x || 0),
        offsetY: event.clientY - (clazz.y || 0)
    };
    document.addEventListener('mousemove', handleClassDrag);
    document.addEventListener('mouseup', endClassDrag);
}

function handleClassDrag(event) {
    if (!crudDragState) return;
    const clazz = crudState.classes.find(cls => cls.id === crudDragState.classId);
    if (!clazz) return;
    clazz.x = event.clientX - crudDragState.offsetX;
    clazz.y = event.clientY - crudDragState.offsetY;
    const node = document.querySelector(`.uml-node[data-id="${clazz.id}"]`);
    if (node) {
        node.style.left = `${clazz.x}px`;
        node.style.top = `${clazz.y}px`;
    }
    renderRelationships();
}

function endClassDrag() {
    crudDragState = null;
    document.removeEventListener('mousemove', handleClassDrag);
    document.removeEventListener('mouseup', endClassDrag);
}

function beginRelationshipDrag(event, classId) {
    event.stopPropagation();
    event.preventDefault();
    const sourceClass = crudState.classes.find(cls => cls.id === classId);
    if (!sourceClass || (sourceClass.structureType || 'CLASS') !== 'CLASS') {
        return;
    }
    const canvas = document.getElementById('umlCanvas');
    if (!canvas) return;
    const svg = document.getElementById('relationshipLayer');
    if (!svg) return;
    const sourceNode = canvas.querySelector(`.uml-node[data-id="${classId}"]`);
    if (!sourceNode) return;
    const canvasRect = canvas.getBoundingClientRect();
    const sourceCenter = getNodeCenter(sourceNode, canvasRect);
    const tempLine = document.createElementNS('http://www.w3.org/2000/svg', 'line');
    tempLine.setAttribute('class', 'relationship-line');
    tempLine.setAttribute('x1', sourceCenter.x);
    tempLine.setAttribute('y1', sourceCenter.y);
    tempLine.setAttribute('x2', sourceCenter.x);
    tempLine.setAttribute('y2', sourceCenter.y);
    svg.appendChild(tempLine);
    relationshipDragState = {
        sourceId: classId,
        line: tempLine,
        start: sourceCenter,
        canvasRect
    };
    document.addEventListener('mousemove', handleRelationshipDrag);
    document.addEventListener('mouseup', endRelationshipDrag);
}

function handleRelationshipDrag(event) {
    if (!relationshipDragState) return;
    const { line, canvasRect, start } = relationshipDragState;
    const x2 = event.clientX - canvasRect.left;
    const y2 = event.clientY - canvasRect.top;
    line.setAttribute('x1', start.x);
    line.setAttribute('y1', start.y);
    line.setAttribute('x2', x2);
    line.setAttribute('y2', y2);
}

function endRelationshipDrag(event) {
    if (!relationshipDragState) return;
    const { line, sourceId } = relationshipDragState;
    document.removeEventListener('mousemove', handleRelationshipDrag);
    document.removeEventListener('mouseup', endRelationshipDrag);
    if (line && line.parentNode) {
        line.parentNode.removeChild(line);
    }
    const targetNode = event.target.closest('.uml-node');
    if (targetNode && targetNode.dataset.id && targetNode.dataset.id !== sourceId) {
        const type = promptRelationshipType();
        if (type) {
            createRelationshipField(sourceId, targetNode.dataset.id, type);
            renderCrudCanvas();
        }
    }
    relationshipDragState = null;
}

function buildCrudPayload() {
    const artifactId = (document.getElementById('artifactId')?.value || '').trim();
    const packageName = (document.getElementById('packageName')?.value || '').trim();
    const moduleName = artifactId ? `${artifactId}-crud` : 'crud-module';
    const basePackage = packageName || 'com.example.demo';

    if (!crudState.classes.length) {
        alert('Adicione ao menos uma classe para gerar o CRUD.');
        return null;
    }

    const classes = [];
    for (const clazz of crudState.classes) {
        ensureStructureFields(clazz);
        const structureType = clazz.structureType || 'CLASS';
        const className = toPascalCase(clazz.name);
        if (!className) {
            alert('Uma das classes está sem nome válido.');
            return null;
        }
        if (structureType === 'CLASS' && !clazz.fields.length) {
            alert(`A classe ${className} precisa de pelo menos um atributo.`);
            return null;
        }
        const fields = [];
        for (const field of clazz.fields) {
            const normalizedName = toCamelCase(field.name);
            if (!normalizedName) {
                continue;
            }
            const fieldPayload = {
                name: normalizedName,
                type: field.type,
                identifier: structureType === 'CLASS' && Boolean(field.identifier),
                required: Boolean(field.required),
                unique: Boolean(field.unique),
                objectType: structureType === 'CLASS' && Boolean(field.objectType)
            };
            if (structureType === 'CLASS' && field.objectType) {
                const targetClass = crudState.classes.find(c => c.id === field.targetClassId);
                if (!targetClass || !targetClass.name || (targetClass.structureType || 'CLASS') !== 'CLASS') {
                    alert('Existe um relacionamento sem classe alvo definida.');
                    return null;
                }
                fieldPayload.targetClassName = targetClass.name;
                fieldPayload.relationshipType = field.relationshipType || relationshipTypes[0].value;
            }
            fields.push(fieldPayload);
        }
        if (structureType === 'CLASS' && !fields.length) {
            alert(`A classe ${className} precisa de atributos válidos.`);
            return null;
        }
        if (structureType === 'CLASS' && !fields.some(field => field.identifier)) {
            fields[0].identifier = true;
        }
        const methods = (clazz.methods || [])
            .map(method => serializeMethod(method, structureType))
            .filter(Boolean);
        const enumConstants = structureType === 'ENUM'
            ? (clazz.enumConstants || [])
                .map(value => toConstantCase(value))
                .filter(Boolean)
            : [];
        classes.push({
            name: className,
            tableName: structureType === 'CLASS' ? (clazz.tableName || '').trim() : '',
            fields,
            structureType,
            methods,
            enumConstants
        });
    }

    return {
        moduleName,
        basePackage,
        classes
    };
}

function serializeMethod(method, structureType) {
    const name = toCamelCase(method.name);
    if (!name) {
        return null;
    }
    const returnType = (method.returnType || 'void').trim() || 'void';
    const parameters = (method.parameters || [])
        .map(param => {
            const type = (param.type || '').trim();
            const paramName = toCamelCase(param.name || '');
            if (!type || !paramName) {
                return null;
            }
            return { type, name: paramName };
        })
        .filter(Boolean);
    const isInterface = structureType === 'INTERFACE';
    return {
        name,
        returnType,
        parameters,
        abstractMethod: isInterface ? false : Boolean(method.abstractMethod),
        defaultImplementation: isInterface ? Boolean(method.defaultImplementation) : false,
        body: method.body || ''
    };
}

function toPascalCase(value) {
    if (!value) return '';
    return value
        .replace(/[^a-zA-Z0-9]+/g, ' ')
        .split(' ')
        .filter(Boolean)
        .map(part => part.charAt(0).toUpperCase() + part.slice(1).toLowerCase())
        .join('');
}

function toCamelCase(value) {
    const pascal = toPascalCase(value);
    if (!pascal) return '';
    return pascal.charAt(0).toLowerCase() + pascal.slice(1);
}

function toConstantCase(value) {
    if (!value) return '';
    return value
        .replace(/[^a-zA-Z0-9]/g, '_')
        .replace(/_+/g, '_')
        .toUpperCase();
}

function crudRandomId(prefix) {
    if (typeof crypto !== 'undefined' && crypto.randomUUID) {
        return `${prefix}-${crypto.randomUUID()}`;
    }
    return `${prefix}-${Date.now()}-${Math.random().toString(16).slice(2)}`;
}

function initTabs() {
    const buttons = document.querySelectorAll('.tab-btn');
    const contents = document.querySelectorAll('.tab-content');
    buttons.forEach(btn => {
        btn.addEventListener('click', () => {
            const target = btn.dataset.tab;
            buttons.forEach(b => b.classList.toggle('active', b === btn));
            contents.forEach(content => {
                content.classList.toggle('active', content.id === target);
            });
        });
    });
}

document.addEventListener('DOMContentLoaded', function() {
    updateSpringVersions();
    updatePackageName();
    updateDependencyCounter();
    initCrudBuilder();
    initTabs();
    window.addEventListener('resize', renderRelationships);

    document.addEventListener('change', function(e) {
        if (e.target && e.target.matches('.dependencies-container input[type="checkbox"]')) {
            updateDependencyCounter();
        }
    });

    const items = document.querySelectorAll('.dependency-item');
    items.forEach(item => {
        const checkbox = item.querySelector('input[type="checkbox"]');
        if (!checkbox) return;
        checkbox.addEventListener('change', function() {
            if (this.checked) {
                item.classList.add('selected');
                moveToTop(item);
            } else {
                item.classList.remove('selected');
                restoreOriginalPosition(item);
            }
            updateDependencyCounter();
        });
        if (checkbox.checked) item.classList.add('selected');
    });
});

function promptRelationshipType() {
    let message = 'Escolha o tipo de relacionamento:\n';
    relationshipTypes.forEach((rel, index) => {
        message += `${index + 1}. ${rel.label}\n`;
    });
    const response = window.prompt(message);
    if (!response) return null;
    const index = parseInt(response, 10) - 1;
    if (isNaN(index) || index < 0 || index >= relationshipTypes.length) {
        alert('Opção inválida.');
        return null;
    }
    return relationshipTypes[index].value;
}

function createRelationshipField(sourceId, targetId, relationshipType) {
    const sourceClass = crudState.classes.find(cls => cls.id === sourceId);
    const targetClass = crudState.classes.find(cls => cls.id === targetId);
    if (!sourceClass || !targetClass) return;
    if ((sourceClass.structureType || 'CLASS') !== 'CLASS'
        || (targetClass.structureType || 'CLASS') !== 'CLASS') {
        alert('Relacionamentos só podem ser criados entre classes concretas.');
        return;
    }
    const baseName = relationshipType === 'ONE_TO_MANY' || relationshipType === 'MANY_TO_MANY'
        ? toCamelCase(targetClass.name || 'Relacionamento') + 's'
        : toCamelCase(targetClass.name || 'Relacionamento');
    const fieldName = generateUniqueFieldName(sourceClass, baseName);
    sourceClass.fields.push({
        id: crudRandomId('fld'),
        name: fieldName,
        type: 'OBJECT',
        identifier: false,
        required: false,
        unique: false,
        objectType: true,
        targetClassId: targetClass.id,
        relationshipType
    });
}

function generateUniqueFieldName(clazz, baseName) {
    let name = baseName || 'relacao';
    let counter = 1;
    const existing = new Set(clazz.fields.map(field => field.name));
    while (existing.has(name)) {
        name = `${baseName}${counter}`;
        counter += 1;
    }
    return name;
}
