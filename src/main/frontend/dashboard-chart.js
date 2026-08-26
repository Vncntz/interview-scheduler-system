import Chart from 'chart.js/auto';

class IssDashboardChart extends HTMLElement {
    connectedCallback() {
        if (!this.shadowRoot) {
            const shadow = this.attachShadow({mode: 'open'});
            const style = document.createElement('style');
            style.textContent = `
                :host {
                    display: block;
                    inline-size: 100%;
                    block-size: 18rem;
                    min-block-size: 16rem;
                }

                .chart-container {
                    position: relative;
                    inline-size: 100%;
                    block-size: 100%;
                }
            `;

            this.canvas = document.createElement('canvas');
            this.canvas.setAttribute('role', 'img');

            const container = document.createElement('div');
            container.className = 'chart-container';
            container.append(this.canvas);
            shadow.append(style, container);
        }

        this.themeObserver = new MutationObserver(() => this.renderChart());
        for (let element = this.parentElement; element; element = element.parentElement) {
            this.themeObserver.observe(element, {
                attributes: true,
                attributeFilter: ['class', 'style', 'theme']
            });
        }

        this.colorSchemeQuery = window.matchMedia('(prefers-color-scheme: dark)');
        this.handleColorSchemeChange = () => this.renderChart();
        this.colorSchemeQuery.addEventListener('change', this.handleColorSchemeChange);

        this.reducedMotionQuery = window.matchMedia('(prefers-reduced-motion: reduce)');
        this.handleReducedMotionChange = () => this.renderChart();
        this.reducedMotionQuery.addEventListener('change', this.handleReducedMotionChange);

        this.renderChart();
    }

    disconnectedCallback() {
        this.themeObserver?.disconnect();
        this.colorSchemeQuery?.removeEventListener('change', this.handleColorSchemeChange);
        this.reducedMotionQuery?.removeEventListener('change', this.handleReducedMotionChange);
        this.chart?.destroy();
        this.chart = undefined;
    }

    setData(type, labels, values, accessibleLabel) {
        this.chartType = type;
        this.labels = Array.from(labels ?? []);
        this.values = Array.from(values ?? []);
        this.accessibleLabel = accessibleLabel;
        const dataDescription = this.labels
            .map((label, index) => `${label} ${this.values[index] ?? 0}`)
            .join(', ');
        const label = accessibleLabel ?? 'Dashboard chart';
        this.canvas?.setAttribute('aria-label', dataDescription ? `${label}: ${dataDescription}` : label);
        this.renderChart();
    }

    renderChart() {
        if (!this.isConnected || !this.canvas || !this.chartType || !this.labels) {
            return;
        }

        this.chart?.destroy();

        const darkMode = this.isDarkMode();
        const textColor = darkMode ? 'rgba(255, 255, 255, 0.72)' : '#64748b';
        const gridColor = darkMode ? 'rgba(255, 255, 255, 0.14)' : 'rgba(15, 23, 42, 0.12)';
        const palette = darkMode
            ? ['#82b8ff', '#4ade80', '#fb7185', '#fbbf24']
            : ['#2563eb', '#16a34a', '#dc2626', '#d97706'];

        const isDoughnut = this.chartType === 'doughnut';
        const dataset = {
            data: this.values,
            backgroundColor: isDoughnut ? palette : palette[0],
            borderWidth: 0,
            spacing: isDoughnut ? 2 : 0,
            borderRadius: isDoughnut ? 0 : 5,
            maxBarThickness: 38
        };

        this.chart = new Chart(this.canvas, {
            type: this.chartType,
            data: {
                labels: this.labels,
                datasets: [dataset]
            },
            options: {
                responsive: true,
                maintainAspectRatio: false,
                animation: {
                    duration: this.reducedMotionQuery?.matches ? 0 : 250
                },
                cutout: isDoughnut ? '68%' : undefined,
                plugins: {
                    legend: {
                        display: isDoughnut,
                        position: 'bottom',
                        labels: {
                            color: textColor,
                            boxWidth: 12,
                            boxHeight: 12,
                            padding: 18,
                            usePointStyle: true
                        }
                    },
                    tooltip: {
                        displayColors: true
                    }
                },
                scales: isDoughnut ? undefined : {
                    x: {
                        grid: {display: false},
                        ticks: {color: textColor}
                    },
                    y: {
                        beginAtZero: true,
                        grid: {color: gridColor},
                        ticks: {
                            color: textColor,
                            precision: 0
                        }
                    }
                }
            }
        });
    }

    isDarkMode() {
        const colorScheme = getComputedStyle(this).colorScheme.trim().toLowerCase();
        if (colorScheme === 'dark') {
            return true;
        }
        if (colorScheme === 'light') {
            return false;
        }

        if (colorScheme.includes('dark') && colorScheme.includes('light')) {
            return this.colorSchemeQuery?.matches ?? false;
        }

        const themedAncestor = this.closest('[theme~="dark"], [theme~="light"]');
        return themedAncestor?.getAttribute('theme')?.toLowerCase().split(/\s+/).includes('dark') ?? false;
    }
}

if (!customElements.get('iss-dashboard-chart')) {
    customElements.define('iss-dashboard-chart', IssDashboardChart);
}
