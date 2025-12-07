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
        structureType: 'CLASS',
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
    const isEntity = (clazz.structureType || 'CLASS') === 'CLASS';
    if (isEntity && !clazz.fields.some(field => field.identifier)) {
        clazz.fields[0].identifier = true;
    }
    renderCrudClassDetail();
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
}

function removeMethodFromClass(classId, methodId) {
    const clazz = crudState.classes.find(cls => cls.id === classId);
    if (!clazz || !Array.isArray(clazz.methods)) return;
    clazz.methods = clazz.methods.filter(method => method.id !== methodId);
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
}

function removeParameterFromMethod(classId, methodId, paramId) {
    const clazz = crudState.classes.find(cls => cls.id === classId);
    const method = clazz?.methods?.find(m => m.id === methodId);
    if (!method || !Array.isArray(method.parameters)) return;
    method.parameters = method.parameters.filter(param => param.id !== paramId);
}

function renderMethodRow(clazz, method) {
    const structureType = clazz.structureType || 'CLASS';
    const isInterface = structureType === 'INTERFACE';
    const row = document.createElement('div');
    row.className = 'method-row';

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
        renderCrudClassDetail();
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
        renderCrudClassDetail();
    });
    actions.appendChild(removeBtn);
    row.appendChild(actions);

    return row;
}

function renderParameterRow(clazz, method, parameter) {
    const row = document.createElement('div');
    row.className = 'parameter-row';
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
        renderCrudClassDetail();
    });
    row.appendChild(typeInput);
    row.appendChild(nameInput);
    row.appendChild(removeBtn);
    return row;
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
        ensureStructureFields(clazz);
        const item = document.createElement('div');
        item.className = 'crud-class-item';
        if (clazz.id === crudState.selectedClassId) {
            item.classList.add('active');
        }
        const title = document.createElement('h4');
        title.textContent = clazz.name;
        const subtitle = document.createElement('small');
        const parts = [];
        const typeLabel = getStructureLabel(clazz.structureType);
        if (typeLabel) {
            parts.push(typeLabel);
        }
        parts.push(`${clazz.fields.length} atributos`);
        subtitle.textContent = parts.join(' · ');
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
    ensureStructureFields(clazz);
    const structureType = clazz.structureType || 'CLASS';
    const isEntity = structureType === 'CLASS';

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

    const typeGroup = document.createElement('div');
    typeGroup.className = 'form-group';
    const typeLabel = document.createElement('label');
    typeLabel.textContent = 'Tipo da Estrutura';
    const typeSelect = document.createElement('select');
    structureTypeOptions.forEach(opt => {
        const option = document.createElement('option');
        option.value = opt.value;
        option.textContent = opt.label;
        if (opt.value === structureType) {
            option.selected = true;
        }
        typeSelect.appendChild(option);
    });
    typeSelect.addEventListener('change', e => {
        handleStructureTypeChange(clazz, e.target.value);
        renderCrudClassDetail();
        renderCrudCanvas();
    });
    typeGroup.appendChild(typeLabel);
    typeGroup.appendChild(typeSelect);

    const tableGroup = document.createElement('div');
    tableGroup.className = 'form-group';
    if (isEntity) {
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
    }

    container.appendChild(classNameGroup);
    container.appendChild(typeGroup);
    if (isEntity) {
        container.appendChild(tableGroup);
    }

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
                renderRelationships();
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
                renderRelationships();
            });
            relationshipGroup.appendChild(relationshipLabel);
            relationshipGroup.appendChild(relationshipSelect);

            objectContainer.appendChild(targetGroup);
            objectContainer.appendChild(relationshipGroup);
            fieldRow.appendChild(objectContainer);
        }

        if (isEntity) {
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

            fieldRow.appendChild(controls);
        }

        const removeBtn = document.createElement('button');
        removeBtn.type = 'button';
        removeBtn.className = 'btn btn-secondary btn-compact';
        removeBtn.textContent = 'Remover';
        removeBtn.addEventListener('click', () => removeAttributeFromClass(clazz.id, field.id));

        const actions = document.createElement('div');
        actions.className = 'row-actions';
        actions.appendChild(removeBtn);

        fieldRow.appendChild(actions);

        container.appendChild(fieldRow);
    });

    const methodsHeader = document.createElement('h4');
    methodsHeader.textContent = 'Métodos';
    container.appendChild(methodsHeader);
    const methodsContainer = document.createElement('div');
    methodsContainer.className = 'method-section';
    if (!clazz.methods.length) {
        const empty = document.createElement('p');
        empty.style.color = '#7f8c8d';
        empty.textContent = 'Nenhum método definido.';
        methodsContainer.appendChild(empty);
    } else {
        clazz.methods.forEach(method => {
            methodsContainer.appendChild(renderMethodRow(clazz, method));
        });
    }
    const addMethodBtn = document.createElement('button');
    addMethodBtn.type = 'button';
    addMethodBtn.className = 'btn btn-secondary btn-compact';
    addMethodBtn.textContent = 'Adicionar método';
    addMethodBtn.addEventListener('click', () => {
        addMethodToClass(clazz.id);
        renderCrudClassDetail();
    });
    methodsContainer.appendChild(addMethodBtn);
    container.appendChild(methodsContainer);

    if (structureType === 'ENUM') {
        const constantsHeader = document.createElement('h4');
        constantsHeader.textContent = 'Constantes';
        container.appendChild(constantsHeader);
        const constantsWrapper = document.createElement('div');
        constantsWrapper.className = 'enum-constants';
        if (!clazz.enumConstants.length) {
            const empty = document.createElement('p');
            empty.style.color = '#7f8c8d';
            empty.textContent = 'Nenhuma constante definida.';
            constantsWrapper.appendChild(empty);
        }
        clazz.enumConstants.forEach((constant, index) => {
            const row = document.createElement('div');
            row.className = 'attribute-row';
            const input = document.createElement('input');
            input.type = 'text';
            input.value = constant || '';
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
                renderCrudClassDetail();
            });
            row.appendChild(input);
            row.appendChild(removeBtn);
            constantsWrapper.appendChild(row);
        });
        const addConstantBtn = document.createElement('button');
        addConstantBtn.type = 'button';
        addConstantBtn.className = 'btn btn-secondary btn-compact';
        addConstantBtn.textContent = 'Adicionar constante';
        addConstantBtn.addEventListener('click', () => {
            clazz.enumConstants.push('VALOR');
            renderCrudClassDetail();
        });
        constantsWrapper.appendChild(addConstantBtn);
        container.appendChild(constantsWrapper);
    }

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
    const relationshipLayer = ensureRelationshipLayer(canvas);
    crudState.classes.forEach(clazz => {
        ensureStructureFields(clazz);
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
        const structureType = clazz.structureType || 'CLASS';
        const headerTitle = document.createElement('span');
        const typeLabel = getStructureLabel(structureType);
        headerTitle.textContent = (clazz.name || 'Classe') + (typeLabel ? ` [${typeLabel}]` : '');
        header.appendChild(headerTitle);
        let linkHandle = null;
        if (structureType === 'CLASS') {
            linkHandle = document.createElement('button');
            linkHandle.type = 'button';
            linkHandle.className = 'uml-link-handle';
            linkHandle.title = 'Criar relacionamento';
            linkHandle.addEventListener('mousedown', e => beginRelationshipDrag(e, clazz.id));
            header.appendChild(linkHandle);
        }
        header.addEventListener('mousedown', e => {
            if (linkHandle && e.target === linkHandle) return;
            beginClassDrag(e, clazz.id);
        });

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
        if (clazz.methods && clazz.methods.length) {
            const methodsList = document.createElement('ul');
            methodsList.className = 'uml-node-fields';
            clazz.methods.forEach(method => {
                const item = document.createElement('li');
                item.textContent = `${method.name || 'metodo'}()`;
                methodsList.appendChild(item);
            });
            node.appendChild(methodsList);
        }
        node.addEventListener('click', () => selectCrudClass(clazz.id));
        canvas.appendChild(node);
    });
    highlightSelectedNode();
    updateCanvasVisibility();
    renderRelationships();
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
            renderCrudClassDetail();
            renderCrudCanvas();
            updateCanvasVisibility();
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
