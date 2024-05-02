import {LitElement, html, css} from 'lit';
import {htmlAssets} from 'build-time-data';
import '@vaadin/grid';
import '@vaadin/grid/vaadin-grid-column.js';
import '@vaadin/split-layout';
import '@quarkus-webcomponents/codeblock';
import '@vaadin/icon';
import { columnBodyRenderer } from '@vaadin/grid/lit.js';

export class QwcWebBundlerHtmlTemplates extends LitElement {

    static styles = css`
        :host {
            height: 100%;
            display: flex;
            padding-left: 10px;
        }
        .codeBlock {
            display: flex;
            padding-left: 10px;
            padding-right: 10px;
        }
        .codeBlock qui-code-block{
            width: 100%;
        }
        .linkOut {
            right: 15px;
            position: absolute;
            top: 5px;
            cursor: pointer;
        }
    `;

    static properties = {
        _htmlAssets: {},
        _selectedHtmlAsset: {state: true}
    };

    constructor() {
        super();
        this._htmlAssets = htmlAssets;
        this._selectedHtmlAsset = this._htmlAssets.slice(0, 1); // Select the first item by default
    }

    render() {
        return html`<vaadin-split-layout style="width: 100%;max-width: 100%;">
                        <master-content>${this._renderHtmlAssets()}</master-content>
                        <detail-content>${this._renderAsset()}</detail-content>
                    </vaadin-split-layout>`;
    }

    _renderHtmlAssets(){
        return html`
            <vaadin-grid .items="${this._htmlAssets}"
                            theme="compact no-border"
                            .selectedItems="${this._selectedHtmlAsset}"    
                            @active-item-changed="${(e) => {
                                const item = e.detail.value;
                                if(item){
                                    this._selectedHtmlAsset = [item];
                                }
                            }}">
                <vaadin-grid-column ${columnBodyRenderer(this._renderPath, [])}></vaadin-grid-column>
            </vaadin-grid>`;
    }

    _renderPath(htmlAsset) {
        return html`
            <code>${htmlAsset.path}</code>
        `;
    }

    _renderAsset(){
        if(this._selectedHtmlAsset && this._selectedHtmlAsset.length > 0){
            return html`<div class="codeBlock">
                            <qui-code-block
                                mode='html'
                                src='/${this._selectedHtmlAsset[0].path}'>
                            </qui-code-block>
                        </div>
                        ${this._renderLinkOut(this._selectedHtmlAsset[0])}`;
        }
    }
    
    _renderLinkOut(htmlAsset){
        return html`<vaadin-icon class="linkOut" 
                        icon="font-awesome-solid:up-right-from-square" 
                        @click=${() => {this._navigate(htmlAsset.path)}}></vaadin-icon>`;
    }

    _navigate(link){
        window.open("/" + link, '_blank').focus();
    }
}

customElements.define('qwc-web-bundler-html-templates', QwcWebBundlerHtmlTemplates)