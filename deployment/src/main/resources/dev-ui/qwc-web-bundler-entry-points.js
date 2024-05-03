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
        .codeBlock qui-code-block{
            width: 100%;
        }
    `;

    static properties = {
        _entryPoints: {},
        _selectedEntryPoint: {state: true},
        _selectedEntryPointContents: {state: true}
    };

    constructor() {
        super();
        this._entryPoints = entryPoints;
        if(this._entryPoints.length>0){
            this._selectedEntryPoint = this._entryPoints[0].items.slice(0, 1); // Select the first item by default
            this._selectedEntryPointContents = this._selectedEntryPoint[0].content;
        }
    }

    render() {
        return html`<vaadin-split-layout style="height: 100%; width: 100%;max-width: 100%;">
                        <master-content>${this._renderEntryPoints()}</master-content>
                        <detail-content class="splitCode">${this._renderCode()}</detail-content>
                    </vaadin-split-layout>`;
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
                                .selectedItems="${this._selectedEntryPoint}"    
                                @active-item-changed="${(e) => {
                                    const item = e.detail.value;
                                    if(item){
                                        this._selectedEntryPoint = [item];
                                        this._selectedEntryPointContents = this._selectedEntryPoint[0].content;
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

    _renderCode(){
        if(this._selectedEntryPointContents){
            return html`<div class="codeBlock">
                            <qui-code-block
                                mode='${this._getFileType(this._selectedEntryPoint[0].path)}' 
                                content='${this._selectedEntryPointContents}'>
                            </qui-code-block>
                        </div>`;
        }else {
            return html`<vaadin-progress-bar indeterminate></vaadin-progress-bar>`;
        }
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