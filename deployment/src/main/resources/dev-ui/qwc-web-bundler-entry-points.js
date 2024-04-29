import {LitElement, html, css} from 'lit';
import {entryPoints} from 'build-time-data';
import '@vaadin/tabsheet';
import '@vaadin/tabs';
import '@vaadin/grid';
import '@vaadin/icon';
import '@vaadin/button';
import '@vaadin/grid/vaadin-grid-column.js';
import { columnBodyRenderer } from '@vaadin/grid/lit.js';


export class QwcWebBundlerEntryPoints extends LitElement {

    static styles = css`
        .full-height {
            height: 100%;
        }
    `;

    static properties = {
        _entryPoints: {},
    };

    constructor() {
        super();
        this._entryPoints = entryPoints;
    }

    render() {
        return html`
            <vaadin-tabsheet class="full-height">
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
            <div tab="${entryPoint.key}" class="full-height">
                <vaadin-grid .itemHasChildrenPath="${'children'}" .items="${entryPoint.items}"
                             theme="compact no-border" class="full-height">
                    <vaadin-grid-column header="Path" ${columnBodyRenderer(this._renderPath, [])}></vaadin-grid-column>
                    <vaadin-grid-column path="type"></vaadin-grid-column>
                </vaadin-grid>
            </div>`;
    }

    _renderPath(entryPoint) {
        return html`
            <code>${entryPoint.path}</code>
        `;
    }



}

customElements.define('qwc-web-bundler-entry-points', QwcWebBundlerEntryPoints)