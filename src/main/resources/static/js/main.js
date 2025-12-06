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

const crudState = {
    classes: [],
    selectedClassId: null
};

let crudDragState = null;

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

    renderCrudClassList();
    renderCrudClassDetail();
    renderCrudCanvas();
    updateCanvasVisibility();
}

function resetCrudBuilder() {
    crudState.classes = [];
    crudState.selectedClassId = null;
    const hidden = document.getElementById('crudDefinition');
    if (hidden) hidden.value = '';
    renderCrudClassList();
    renderCrudClassDetail();
    renderCrudCanvas();
    updateCanvasVisibility();
}

function addCrudClass() {
    const id = crudRandomId('cls');
    const newClass = {
        id,
        name: `Class${crudState.classes.length + 1}`,
        tableName: '',
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
        ]
    };
    crudState.classes.push(newClass);
    crudState.selectedClassId = newClass.id;
    renderCrudClassList();
    renderCrudClassDetail();
    renderCrudCanvas();
    updateCanvasVisibility();
}

function selectCrudClass(classId) {
    crudState.selectedClassId = classId;
    renderCrudClassList();
    renderCrudClassDetail();
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
    renderCrudClassList();
    renderCrudClassDetail();
    renderCrudCanvas();
    updateCanvasVisibility();
}

function addAttributeToClass(classId) {
    const clazz = crudState.classes.find(cls => cls.id === classId);
    if (!clazz) return;
    const newField = {
        id: crudRandomId('fld'),
        name: `field${clazz.fields.length}`,
        type: 'String',
        identifier: false,
        required: false,
        unique: false,
        objectType: false,
        targetClassId: null,
        relationshipType: relationshipTypes[0].value
    };
    clazz.fields.push(newField);
    renderCrudClassDetail();
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
    if (!clazz.fields.some(field => field.identifier)) {
        clazz.fields[0].identifier = true;
    }
    renderCrudClassDetail();
    renderCrudCanvas();
}

function renderCrudClassList() {
    const wrapper = document.getElementById('crudClassList');
    if (!wrapper) return;
    wrapper.innerHTML = '';
    if (!crudState.classes.length) {
        wrapper.innerHTML = '<p style="color:#7f8c8d;">Nenhuma classe adicionada ainda.</p>';
        return;
    }
    crudState.classes.forEach(clazz => {
        const item = document.createElement('div');
        item.className = 'crud-class-item';
        if (clazz.id === crudState.selectedClassId) {
            item.classList.add('active');
        }
        const title = document.createElement('h4');
        title.textContent = clazz.name;
        const subtitle = document.createElement('small');
        subtitle.textContent = `${clazz.fields.length} atributos`;
        item.appendChild(title);
        item.appendChild(subtitle);
        item.addEventListener('click', () => selectCrudClass(clazz.id));
        wrapper.appendChild(item);
    });
}

function renderCrudClassDetail() {
    const container = document.getElementById('crudClassDetail');
    if (!container) return;
    container.innerHTML = '';
    const clazz = crudState.classes.find(cls => cls.id === crudState.selectedClassId);
    if (!clazz) {
        container.innerHTML = '<p>Selecione uma classe para editar seus atributos.</p>';
        return;
    }

    const classNameGroup = document.createElement('div');
    classNameGroup.className = 'form-group';
    const classLabel = document.createElement('label');
    classLabel.textContent = 'Nome da Classe';
    const classInput = document.createElement('input');
    classInput.type = 'text';
    classInput.value = clazz.name;
    classInput.addEventListener('input', e => {
        clazz.name = toPascalCase(e.target.value);
        e.target.value = clazz.name;
        renderCrudClassList();
        renderCrudCanvas();
    });
    classNameGroup.appendChild(classLabel);
    classNameGroup.appendChild(classInput);

    const tableGroup = document.createElement('div');
    tableGroup.className = 'form-group';
    const tableLabel = document.createElement('label');
    tableLabel.textContent = 'Nome da Tabela (opcional)';
    const tableInput = document.createElement('input');
    tableInput.type = 'text';
    tableInput.placeholder = 'users';
    tableInput.value = clazz.tableName || '';
    tableInput.addEventListener('input', e => {
        clazz.tableName = e.target.value;
    });
    tableGroup.appendChild(tableLabel);
    tableGroup.appendChild(tableInput);

    container.appendChild(classNameGroup);
    container.appendChild(tableGroup);

    const attrsHeader = document.createElement('h4');
    attrsHeader.textContent = 'Atributos';
    container.appendChild(attrsHeader);

    clazz.fields.forEach(field => {
        const fieldRow = document.createElement('div');
        fieldRow.className = 'attribute-row';

        const nameInput = document.createElement('input');
        nameInput.type = 'text';
        nameInput.value = field.name;
        nameInput.placeholder = 'nomeCampo';
        nameInput.addEventListener('input', e => {
            field.name = toCamelCase(e.target.value);
            e.target.value = field.name;
            renderCrudCanvas();
        });

        const typeSelect = document.createElement('select');
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
                    field.targetClassId = crudState.classes[0].id;
                }
            } else {
                field.objectType = false;
                field.targetClassId = null;
                field.relationshipType = null;
                field.type = value;
            }
            renderCrudClassDetail();
            renderCrudCanvas();
        });

        const nameLabel = document.createElement('label');
        nameLabel.textContent = 'Nome';
        nameLabel.style.display = 'block';
        nameLabel.style.fontSize = '0.85em';
        const typeLabel = document.createElement('label');
        typeLabel.textContent = 'Tipo';
        typeLabel.style.display = 'block';
        typeLabel.style.fontSize = '0.85em';

        const nameWrapper = document.createElement('div');
        nameWrapper.className = 'form-group';
        nameWrapper.appendChild(nameLabel);
        nameWrapper.appendChild(nameInput);

        const typeWrapper = document.createElement('div');
        typeWrapper.className = 'form-group';
        typeWrapper.appendChild(typeLabel);
        typeWrapper.appendChild(typeSelect);

        const topRow = document.createElement('div');
        topRow.className = 'attribute-row-row';
        topRow.appendChild(nameWrapper);
        topRow.appendChild(typeWrapper);
        fieldRow.appendChild(topRow);

        if (field.objectType) {
            const objectContainer = document.createElement('div');
            objectContainer.className = 'object-field-config';

            const targetGroup = document.createElement('div');
            targetGroup.className = 'form-group';
            const targetLabel = document.createElement('label');
            targetLabel.textContent = 'Classe alvo';
            const targetSelect = document.createElement('select');
            crudState.classes.forEach(targetClass => {
                const option = document.createElement('option');
                option.value = targetClass.id;
                option.textContent = targetClass.name;
                if (targetClass.id === field.targetClassId) {
                    option.selected = true;
                }
                targetSelect.appendChild(option);
            });
            if (!field.targetClassId && crudState.classes.length) {
                field.targetClassId = crudState.classes[0].id;
                targetSelect.value = field.targetClassId;
            }
            targetSelect.addEventListener('change', e => {
                field.targetClassId = e.target.value;
            });
            targetGroup.appendChild(targetLabel);
            targetGroup.appendChild(targetSelect);

            const relationshipGroup = document.createElement('div');
            relationshipGroup.className = 'form-group';
            const relationshipLabel = document.createElement('label');
            relationshipLabel.textContent = 'Relacionamento';
            const relationshipSelect = document.createElement('select');
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
            });
            relationshipGroup.appendChild(relationshipLabel);
            relationshipGroup.appendChild(relationshipSelect);

            objectContainer.appendChild(targetGroup);
            objectContainer.appendChild(relationshipGroup);
            fieldRow.appendChild(objectContainer);
        }

        const controls = document.createElement('div');
        controls.className = 'attribute-controls';

        controls.appendChild(createCheckbox('Identificador', field.identifier, checked => {
            if (!checked) {
                field.identifier = false;
                if (!clazz.fields.some(f => f.identifier)) {
                    field.identifier = true;
                }
            } else {
                clazz.fields.forEach(f => { f.identifier = f.id === field.id; });
            }
            renderCrudClassDetail();
            renderCrudCanvas();
        }));

        controls.appendChild(createCheckbox('Obrigatório', field.required, checked => {
            field.required = checked;
        }));

        controls.appendChild(createCheckbox('Único', field.unique, checked => {
            field.unique = checked;
        }));

        const removeBtn = document.createElement('button');
        removeBtn.type = 'button';
        removeBtn.className = 'btn btn-secondary btn-compact';
        removeBtn.textContent = 'Remover';
        removeBtn.addEventListener('click', () => removeAttributeFromClass(clazz.id, field.id));

        const actions = document.createElement('div');
        actions.className = 'row-actions';
        actions.appendChild(removeBtn);

        fieldRow.appendChild(controls);
        fieldRow.appendChild(actions);

        container.appendChild(fieldRow);
    });

    const addAttributeBtn = document.createElement('button');
    addAttributeBtn.type = 'button';
    addAttributeBtn.className = 'btn btn-secondary btn-compact';
    addAttributeBtn.style.marginTop = '10px';
    addAttributeBtn.textContent = 'Adicionar atributo';
    addAttributeBtn.addEventListener('click', () => addAttributeToClass(clazz.id));

    const deleteClassBtn = document.createElement('button');
    deleteClassBtn.type = 'button';
    deleteClassBtn.className = 'btn btn-secondary btn-compact';
    deleteClassBtn.style.marginLeft = '10px';
    deleteClassBtn.textContent = 'Excluir classe';
    deleteClassBtn.addEventListener('click', () => removeCrudClass(clazz.id));

    const footer = document.createElement('div');
    footer.style.marginTop = '12px';
    footer.appendChild(addAttributeBtn);
    footer.appendChild(deleteClassBtn);

    container.appendChild(footer);
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

function renderCrudCanvas() {
    const canvas = document.getElementById('umlCanvas');
    if (!canvas) return;
    canvas.innerHTML = '';
    crudState.classes.forEach(clazz => {
        const node = document.createElement('div');
        node.className = 'uml-node';
        if (clazz.id === crudState.selectedClassId) {
            node.classList.add('selected');
        }
        node.dataset.id = clazz.id;
        node.style.left = `${clazz.x || 40}px`;
        node.style.top = `${clazz.y || 40}px`;

        const header = document.createElement('div');
        header.className = 'uml-node-header';
        header.textContent = clazz.name || 'Classe';
        header.addEventListener('mousedown', e => beginClassDrag(e, clazz.id));

        const body = document.createElement('ul');
        body.className = 'uml-node-fields';
        clazz.fields.forEach(field => {
            const item = document.createElement('li');
            if (field.identifier) {
                item.classList.add('is-id');
            }
            item.textContent = describeField(field);
            body.appendChild(item);
        });

        node.appendChild(header);
        node.appendChild(body);
        node.addEventListener('click', () => selectCrudClass(clazz.id));
        canvas.appendChild(node);
    });
    highlightSelectedNode();
    updateCanvasVisibility();
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
}

function endClassDrag() {
    crudDragState = null;
    document.removeEventListener('mousemove', handleClassDrag);
    document.removeEventListener('mouseup', endClassDrag);
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
        const className = toPascalCase(clazz.name);
        if (!className) {
            alert('Uma das classes está sem nome válido.');
            return null;
        }
        if (!clazz.fields.length) {
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
                identifier: Boolean(field.identifier),
                required: Boolean(field.required),
                unique: Boolean(field.unique),
                objectType: Boolean(field.objectType)
            };
            if (field.objectType) {
                const targetClass = crudState.classes.find(c => c.id === field.targetClassId);
                if (!targetClass || !targetClass.name) {
                    alert('Existe um relacionamento sem classe alvo definida.');
                    return null;
                }
                fieldPayload.targetClassName = targetClass.name;
                fieldPayload.relationshipType = field.relationshipType || relationshipTypes[0].value;
            }
            fields.push(fieldPayload);
        }
        if (!fields.length) {
            alert(`A classe ${className} precisa de atributos válidos.`);
            return null;
        }
        if (!fields.some(field => field.identifier)) {
            fields[0].identifier = true;
        }
        classes.push({
            name: className,
            tableName: (clazz.tableName || '').trim(),
            fields
        });
    }

    return {
        moduleName,
        basePackage,
        classes
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
