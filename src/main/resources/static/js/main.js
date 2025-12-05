// Auto-sync functionality and UI helpers moved from template

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
    if (!autoPackage || !autoPackage.checked) return;
    const groupId = document.getElementById('groupId')?.value || '';
    const artifactId = document.getElementById('artifactId')?.value || '';
    const packageNameInput = document.getElementById('packageName');
    if (!packageNameInput) return;
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
            // fallback: mantém opções atuais se a API falhar
        });
}

function resetForm() {
    const form = document.querySelector('form');
    if (!form) return;
    form.reset();
    const auto = document.getElementById('autoPackage');
    if (auto) auto.checked = true;
    updateSpringVersions();
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
    const originalIndex = parseInt(item.dataset.originalIndex);
    if (isNaN(originalIndex)) return;
    const items = Array.from(grid.children);
    let insertBeforeElement = null;
    for (let i = 0; i < items.length; i++) {
        const currentItem = items[i];
        const currentOriginalIndex = parseInt(currentItem.dataset.originalIndex);
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

document.addEventListener('DOMContentLoaded', function() {
    // inicializações
    updateSpringVersions();
    updatePackageName();
    updateDependencyCounter();

    // delegação para atualizar contador ao mudar checkboxes (melhora performance)
    document.addEventListener('change', function(e) {
        if (e.target && e.target.matches('.dependencies-container input[type="checkbox"]')) {
            updateDependencyCounter();
        }
    });

    // ligar handlers de seleção que movem visualmente os itens (se existirem)
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
