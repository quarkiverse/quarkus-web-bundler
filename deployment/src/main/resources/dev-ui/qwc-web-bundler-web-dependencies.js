import {LitElement, html, css} from 'lit';
import {webDependencyLibraries} from 'build-time-data';
import '@vaadin/tabs';
import '@vaadin/grid';
import '@vaadin/icon';
import '@vaadin/button';
import '@vaadin/grid/vaadin-grid-tree-column.js';
import {notifier} from 'notifier';
import {columnBodyRenderer} from '@vaadin/grid/lit.js';

export class QwcWebBundlerWebDependencies extends LitElement {
    static styles = css`
        :host {
            display: flex;
            height: 100%;
            padding: 10px;
            gap: 20px;
        }
        
        .tabcontent {
            height: 100%;
            width: 100%;
        }
    
        .full-height {
            height: 100%;
        }
    `;

    static properties = {
        _webDependencyLibraries: {},
        _selectedWebDependency: {state: true}
    };

    constructor() {
        super();
        this._webDependencyLibraries = webDependencyLibraries;
        this._selectedWebDependency = this._webDependencyLibraries[0];
    }

    render() {
        return html`
                <vaadin-tabs @selected-changed="${this._tabSelectedChanged}" orientation="vertical">
                    ${this._webDependencyLibraries.map(webDependency => html`
                        <vaadin-tab id="${webDependency.webDependencyName}">
                            ${webDependency.webDependencyName + " (" + webDependency.version + ")"}
                        </vaadin-tab>`)}
                </vaadin-tabs>

                ${this._renderLibraryAssets(this._selectedWebDependency)}
        `;
    }

    _tabSelectedChanged(e){
        this._selectedWebDependency = this._webDependencyLibraries[e.detail.value];
    }

    _renderLibraryAssets(library) {
        const dataProvider = function (params, callback) {
            if (params.parentItem === undefined) {
                callback(library.rootAsset.children, library.rootAsset.children.length);
            } else {
                callback(params.parentItem.children, params.parentItem.children.length)
            }
        };

        return html`
            <div tab="${library.webDependencyName}" class="tabcontent">
                <vaadin-grid .itemHasChildrenPath="${'children'}" .dataProvider="${dataProvider}"
                             theme="compact no-border" class="full-height">
                    <vaadin-grid-tree-column path="name"></vaadin-grid-tree-column>
                </vaadin-grid>
            </div>`;
    }
}

customElements.define('qwc-web-bundler-web-dependencies', QwcWebBundlerWebDependencies)
