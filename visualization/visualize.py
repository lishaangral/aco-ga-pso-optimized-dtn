import matplotlib.pyplot as plt
import numpy as np

# Set font to serif for IEEE/Springer compliance
plt.rcParams['font.family'] = 'serif'
plt.rcParams['font.size'] = 11

# Data
models = ['Base\n(ACO-GSO)', 'GA-PSO', 'ACO-GA', 'ACO-PSO', 'Proposed\n(ACO-GA-PSO)']  # Line break for better label fitting
x = np.arange(len(models))

# Exact color palette inspired by your uploaded image
colors = ['#8598b0', '#75b05a', '#a4d284', '#fabd0f', '#737578']

# Metrics
delivery_30 = [82.81, 92.24, 94.36, 91.40, 89.09]
delivery_60 = [89.19, 96.12, 96.12, 96.30, 93.90]

latency_30 = [772.9, 556.8, 533.1, 653.9, 653.4]
latency_60 = [935.3, 613.1, 578.2, 746.6, 852.5]

overhead_30 = [24.91, 18.74, 21.11, 17.51, 16.23]
overhead_60 = [32.70, 19.53, 20.77, 16.31, 13.70]

removed_30 = [13579, 18621, 21727, 16967, 1135]
removed_60 = [21906, 20808, 22136, 17684, 1173]

def create_single_chart(data, title, ylabel, filename, y_limit):
    # Optimized for a single IEEE column (approx 3.5 inches wide)
    fig, ax = plt.subplots(figsize=(5.5, 4.5))
    
    # Set the soft yellow/beige background from your reference image
    fig.patch.set_facecolor('#FFF9ED')
    ax.set_facecolor('#FFF9ED')
    
    # Create bars
    bars = ax.bar(x, data, width=0.65, color=colors, edgecolor='black', linewidth=0.8)

    ax.set_ylabel(ylabel, fontweight='bold', fontsize=12)
    ax.set_title(title, fontweight='bold', fontsize=13, pad=45) # Pad to make room for legend
    ax.set_xticks(x)
    ax.set_xticklabels(models, fontsize=10)
    
    # Horizontal Legend at the very top
    from matplotlib.patches import Patch
    legend_elements = [Patch(facecolor=colors[i], edgecolor='black', label=models[i].replace('\n', ' ')) for i in range(5)]
    
    # Place legend above the title
    ax.legend(handles=legend_elements, loc='lower center', bbox_to_anchor=(0.5, 1.02), 
              ncol=3, frameon=False, fontsize=9.5)
    
    ax.set_ylim(0, y_limit)
    
    # Clean gridlines behind the bars
    ax.grid(axis='y', linestyle='--', alpha=0.6)
    ax.set_axisbelow(True)
    
    # Hide top and right spines for a cleaner look
    ax.spines['top'].set_visible(False)
    ax.spines['right'].set_visible(False)
    
    # Add bold, legible numbers on top of each bar
    for bar in bars:
        height = bar.get_height()
        # Format: 1,000 for large numbers, 10.1 for small numbers
        label_text = f'{height:,.1f}' if height < 1000 else f'{int(height):,}'
        ax.annotate(label_text,
                    xy=(bar.get_x() + bar.get_width() / 2, height),
                    xytext=(0, 4),  # 4 points vertical offset
                    textcoords="offset points",
                    ha='center', va='bottom', fontsize=11, fontweight='bold')
    
    plt.tight_layout()
    plt.savefig(filename, format='pdf', dpi=300, bbox_inches='tight', facecolor=fig.get_facecolor())
    plt.close()
    print(f"Generated {filename}")

# Generate all 8 charts with consistent Y-limits for fair comparison between 30 and 60 TTL
create_single_chart(delivery_30, 'Delivery Probability (30 Min TTL)', 'Delivery Probability (%)', 'delivery_30.pdf', 110)
create_single_chart(delivery_60, 'Delivery Probability (60 Min TTL)', 'Delivery Probability (%)', 'delivery_60.pdf', 110)

create_single_chart(latency_30, 'Average Latency (30 Min TTL)', 'Latency (Seconds)', 'latency_30.pdf', 1100)
create_single_chart(latency_60, 'Average Latency (60 Min TTL)', 'Latency (Seconds)', 'latency_60.pdf', 1100)

create_single_chart(overhead_30, 'Network Overhead Ratio (30 Min TTL)', 'Overhead Ratio', 'overhead_30.pdf', 40)
create_single_chart(overhead_60, 'Network Overhead Ratio (60 Min TTL)', 'Overhead Ratio', 'overhead_60.pdf', 40)

create_single_chart(removed_30, 'Buffer Churn (30 Min TTL)', 'Number of Removed Bundles', 'removed_30.pdf', 26000)
create_single_chart(removed_60, 'Buffer Churn (60 Min TTL)', 'Number of Removed Bundles', 'removed_60.pdf', 26000)