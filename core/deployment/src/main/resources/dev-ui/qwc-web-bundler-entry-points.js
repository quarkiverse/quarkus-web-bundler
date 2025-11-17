import {LitElement, html, css} from 'lit';
import {entryPoints} from 'build-time-data';
import '@vaadin/tabsheet';
import '@vaadin/tabs';
import '@vaadin/grid';
import '@vaadin/grid/vaadin-grid-column.js';
import '@vaadin/split-layout';
import '@vaadin/progress-bar';
import '@quarkus-webcomponents/codeblock';
import { columnBodyRenderer } from '@vaadin/grid/lit.js';


export class QwcWebBundlerEntryPoints extends LitElement {

    static styles = css`
        :host {
            height: 100%;
            display: flex;
            flex-direction: column;
        }

        vaadin-tabsheet, vaadin-grid, .tab {
            height: 100%;
        }
        .splitCode {
            display: flex;
            padding-left: 10px;
            padding-right: 10px;
        }
        .codeBlock {
            display: flex;
            width: 100%;
        }
        master-content {
            min-width: 300px;
        }
        .codeBlock qui-code-block{
            width: 100%;
        }
    `;

    static properties = {
        _entryPoints: {},
        _selectedEntryPoint: {state: true},
    };

    constructor() {
        super();
        this._entryPoints = entryPoints;
        if(this._entryPoints.length>0){
            this._selectedEntryPoint = this._entryPoints[0].items[0]; // Select the first item by default
        }
    }

    render() {
        return this._renderEntryPoints();
    }

    _renderEntryPoints(){
        return html`
            <vaadin-tabsheet>
                <vaadin-tabs slot="tabs">
                    ${this._entryPoints.map(entryPoint => html`
                        <vaadin-tab id="${entryPoint.key}">
                            ${entryPoint.key}
                        </vaadin-tab>`)}
                </vaadin-tabs>

                ${this._entryPoints.map(entryPoint => this._renderEntryPoint(entryPoint))}
            </vaadin-tabsheet>
        `;
    }

    _renderEntryPoint(entryPoint) {

        return html`
            <div tab="${entryPoint.key}" class="tab">
                <vaadin-grid .items="${entryPoint.items}"
                                theme="compact no-border"
                                .selectedItems="${[this._selectedEntryPoint]}"    
                                @active-item-changed="${(e) => {
                                    const item = e.detail.value;
                                    if(item){
                                        this._selectedEntryPoint = item;
                                    }
                                }}">
                    <vaadin-grid-column header="Path" ${columnBodyRenderer(this._renderPath, [])} resizable auto-width></vaadin-grid-column>
                    <vaadin-grid-column path="type" resizable width="2em"></vaadin-grid-column>
                </vaadin-grid>
            </div>`;
    }

    _renderPath(entryPoint) {
        return html`
            <code>${entryPoint.path}</code>
        `;
    }

    _getFileType(filename) {
        let lastDotIndex = filename.lastIndexOf('.');

        if (lastDotIndex === -1 || lastDotIndex === 0) {
            return 'js'; // default
        } else {
            return filename.substring(lastDotIndex + 1);
        }
    }

}

customElements.define('qwc-web-bundler-entry-points', QwcWebBundlerEntryPoints)