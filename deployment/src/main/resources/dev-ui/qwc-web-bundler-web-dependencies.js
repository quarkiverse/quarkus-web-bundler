import {LitElement, html, css} from 'lit';
import {webDependencies} from 'build-time-data';
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

        vaadin-tabs {
            white-space: nowrap;
        }
    
        .full-height {
            height: 100%;
        }
    `;

    static properties = {
        _webDependencies: {},
        _selectedWebDependencies: {state: true}
    };

    constructor() {
        super();
        this._webDependencies = webDependencies;
        this._selectedWebDependencies = this._webDependencies[0];
    }

    render() {
        return html`
                <vaadin-tabs @selected-changed="${this._tabSelectedChanged}" orientation="vertical">
                    ${this._webDependencies.map(webDependency => html`
                        <vaadin-tab id="${webDependency.name}">
                            ${webDependency.name + " (" + webDependency.version + ")"}
                        </vaadin-tab>`)}
                </vaadin-tabs>

                ${this._renderAssets(this._selectedWebDependencies)}
        `;
    }

    _tabSelectedChanged(e){
        this._selectedWebDependencies = this._webDependencies[e.detail.value];
    }

    _renderAssets(dep) {
        const dataProvider = function (params, callback) {
            if (params.parentItem === undefined) {
                callback(dep.rootAsset.children, dep.rootAsset.children.length);
            } else {
                callback(params.parentItem.children, params.parentItem.children.length)
            }
        };

        return html`
            <div tab="${dep.name}" class="tabcontent">
                <vaadin-grid .itemHasChildrenPath="${'children'}" .dataProvider="${dataProvider}"
                             theme="compact no-border" class="full-height">
                    <vaadin-grid-tree-column path="name"></vaadin-grid-tree-column>
                </vaadin-grid>
            </div>`;
    }
}

customElements.define('qwc-web-bundler-web-dependencies', QwcWebBundlerWebDependencies)
