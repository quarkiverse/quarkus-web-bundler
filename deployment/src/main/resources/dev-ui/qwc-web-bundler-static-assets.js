import {LitElement, html, css} from 'lit';
import {staticAssets} from 'build-time-data';
import '@vaadin/grid';
import '@vaadin/grid/vaadin-grid-column.js';
import '@vaadin/split-layout';
import { columnBodyRenderer } from '@vaadin/grid/lit.js';

export class QwcWebBundlerStaticAssets extends LitElement {

    static styles = css`
        :host {
            height: 100%;
            display: flex;
            padding-left: 10px;
        }
        .preview {
            display: flex;
            justify-content: center;
            padding: 50px 10px 10px;
        }
        .preview img{
            max-height: 100%;
            max-width: 100%;
        }
        .linkOut {
            right: 15px;
            position: absolute;
            top: 5px;
            cursor: pointer;
        }
    `;

    static properties = {
        _staticAssets: {},
        _selectedStaticAsset: {state: true}
    };

    constructor() {
        super();
        this._staticAssets = staticAssets;
        this._selectedStaticAsset = this._staticAssets.slice(0, 1); // Select the first item by default
    }

    render() {
        return html`<vaadin-split-layout style="width: 100%;max-width: 100%;">
                        <master-content>${this._renderStaticAssets()}</master-content>
                        <detail-content>${this._renderAsset()}</detail-content>
                    </vaadin-split-layout>`;
    }

    _renderStaticAssets(){
        return html`
            <vaadin-grid .items="${this._staticAssets}"
                            theme="compact no-border"
                            .selectedItems="${this._selectedStaticAsset}"    
                            @active-item-changed="${(e) => {
                                const item = e.detail.value;
                                if(item){
                                    this._selectedStaticAsset = [item];
                                }
                            }}">
                <vaadin-grid-column ${columnBodyRenderer(this._renderPath, [])}></vaadin-grid-column>
            </vaadin-grid>`;
    }

    _renderPath(entryPoint) {
        return html`
            <code>${entryPoint.path}</code>
        `;
    }

    _renderAsset(){
        if(this._selectedStaticAsset && this._selectedStaticAsset.length > 0){
            return html`<div class="preview">
                            <img src="${this._selectedStaticAsset[0].path}"></img>
                        </div>
                        ${this._renderLinkOut(this._selectedStaticAsset[0])}`;
        }
    }
    
    _renderLinkOut(staticAsset){
        return html`<vaadin-icon class="linkOut" 
                        icon="font-awesome-solid:up-right-from-square" 
                        @click=${() => {this._navigate(staticAsset.path)}}></vaadin-icon>`;
    }

    _navigate(link){
        window.open(link, '_blank').focus();
    }
}

customElements.define('qwc-web-bundler-static-assets', QwcWebBundlerStaticAssets)