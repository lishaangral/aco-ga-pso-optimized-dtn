# Hybrid Evolutionary Routing in Delay Tolerant Networks

This repository contains the implementation and experimental setup for a **Delay Tolerant Network (DTN)** routing study based on hybrid evolutionary and swarm intelligence techniques.

The project evaluates pairwise hybrids of:

- **Ant Colony Optimisation (ACO)**
- **Genetic Algorithm (GA)**
- **Particle Swarm Optimisation (PSO)**

It also compares them against the baseline router proposed by **Azzoug**, which is based on:

- **Ant Colony Optimisation (ACO)**
- **Glowworm Swarm Optimisation (GSO)**

Finally, the repository includes the proposed final hybrid router **Ant Colony Optimisation (ACO)** , **Genetic Algorithm (GA)** and  **Particle Swarm Optimisation (PSO)** 
---

## Objective

The purpose of this project is to compare different hybrid evolutionary routing strategies in Delay Tolerant Networks and identify which combination performs best.

The comparison is mainly based on the following metrics:

- **Delivery probability**
- **Overhead ratio**
- **Latency**
- **Number of removed bundles**

---

## Implemented Routers

The repository includes the following routing approaches:

- **ACO + GSO** — baseline router proposed by Azzoug
- **ACO + GA**
- **ACO + PSO**
- **GA + PSO**
- **ACO + GA + PSO** — proposed final hybrid

---

## Repository Structure

```text
.
├── custom_settings/
│   ├── aco_ga.txt
│   ├── aco_pso.txt
│   ├── baseline_30.txt
│   ├── baseline_60.txt
│   ├── final_settings.txt
│   └── ga_pso.txt
│
├── metrics/
│   ├── aco_ga_results.txt
│   ├── aco_pso_results.txt
│   ├── baseline_final.txt
│   ├── ga_pso_results.txt
│   └── proposed_solution.txt
│
├── routing/
│   ├── AcoGARouter.java
│   ├── AcoPsoGaRouter.java
│   ├── AcoPsoRouter.java
│   ├── GaPsoRouter.java
│   └── HybridSwarmRouter.java
│
├── visualisation/
│   └── [bar chart plots]
│
├── compile.bat
├── one.bat
└── ...
```

---

## Settings Files

All simulation settings files are stored in the **`custom_settings`** folder.

| File | Description |
|------|-------------|
| `aco_ga.txt` | Settings for the **Ant Colony Optimisation + Genetic Algorithm (ACO + GA)** hybrid |
| `aco_pso.txt` | Settings for the **Ant Colony Optimisation + Particle Swarm Optimisation (ACO + PSO)** hybrid |
| `ga_pso.txt` | Settings for the **Genetic Algorithm + Particle Swarm Optimisation (GA + PSO)** hybrid |
| `baseline_30.txt` | Settings for the **baseline Ant Colony Optimisation + Glowworm Swarm Optimisation (ACO + GSO)** router with **Time To Live (TTL) = 30 minutes** |
| `baseline_60.txt` | Settings for the **baseline Ant Colony Optimisation + Glowworm Swarm Optimisation (ACO + GSO)** router with **Time To Live (TTL) = 60 minutes** |
| `final_settings.txt` | Settings for the **final proposed hybrid router** |

### Notes

- All settings files are configured **by default for 110 nodes**.
- To change the number of nodes, edit the values inside the corresponding settings file.
- To change the **Time To Live (TTL)** of messages, modify:

```text
Group.msgTtL
```

inside the chosen settings file.

---

## Router Files

All router implementations are stored in the **`routing`** folder.

| File | Description |
|------|-------------|
| `AcoGARouter.java` | Router implementation for **ACO + GA** |
| `AcoPsoRouter.java` | Router implementation for **ACO + PSO** |
| `GaPsoRouter.java` | Router implementation for **GA + PSO** |
| `HybridSwarmRouter.java` | Baseline router proposed by **Azzoug** using **ACO + GSO** |
| `AcoPsoGaRouter.java` | Router implementation for the **final proposed hybrid** |

---

## Results

All precomputed results are stored in the **`metrics`** folder.

| File | Description |
|------|-------------|
| `aco_ga_results.txt` | Results for the **ACO + GA** router |
| `aco_pso_results.txt` | Results for the **ACO + PSO** router |
| `ga_pso_results.txt` | Results for the **GA + PSO** router |
| `baseline_final.txt` | Results for the **baseline ACO + GSO router proposed by Azzoug** |
| `proposed_solution.txt` | Results for the **final proposed hybrid router** |

### Result Scope

These results are based on:

- **110 nodes**
- **TTL = 30 minutes and 60 minutes**
- **Single-run experimentation**

---

## Visualisations

The **`visualisation`** folder contains bar chart plots comparing all five routing approaches for:

- **110 nodes**
- **TTL = 30 minutes**
- **TTL = 60 minutes**

The visual comparison is based on:

- **Delivery probability**
- **Overhead ratio**
- **Latency**
- **Number of removed bundles**

Compared routing approaches:

- **ACO + GSO** — baseline router proposed by Azzoug
- **GA + PSO**
- **ACO + GA**
- **ACO + PSO**
- **ACO + GA + PSO** — proposed

---

## How to Run the Simulation

These instructions assume the repository is being run on **Windows**, since the project uses Windows batch files such as `compile.bat` and `one.bat`.

### 1. Compile the Project

Open a terminal in the root directory of the repository and run:

```bat
.\compile.bat
```

This compiles the Java source files into the `target` directory.

---

### 2. Run a Simulation from the Command Line

Use the following command format:

```bat
java -Xmx512M -cp target;lib/ECLA.jar;lib/DTNConsoleConnection.jar core.DTNSim custom_settings\<settings_file>.txt
```

### Example Commands

#### Run ACO + GA
```bat
java -Xmx512M -cp target;lib/ECLA.jar;lib/DTNConsoleConnection.jar core.DTNSim custom_settings\aco_ga.txt
```

#### Run ACO + PSO
```bat
java -Xmx512M -cp target;lib/ECLA.jar;lib/DTNConsoleConnection.jar core.DTNSim custom_settings\aco_pso.txt
```

#### Run GA + PSO
```bat
java -Xmx512M -cp target;lib/ECLA.jar;lib/DTNConsoleConnection.jar core.DTNSim custom_settings\ga_pso.txt
```

#### Run baseline ACO + GSO with TTL 30 minutes
```bat
java -Xmx512M -cp target;lib/ECLA.jar;lib/DTNConsoleConnection.jar core.DTNSim custom_settings\baseline_30.txt
```

#### Run baseline ACO + GSO with TTL 60 minutes
```bat
java -Xmx512M -cp target;lib/ECLA.jar;lib/DTNConsoleConnection.jar core.DTNSim custom_settings\baseline_60.txt
```

#### Run the final proposed hybrid router
```bat
java -Xmx512M -cp target;lib/ECLA.jar;lib/DTNConsoleConnection.jar core.DTNSim custom_settings\final_settings.txt
```

---

### 3. Run in Batch Mode

To run the same simulation multiple times, use batch mode with `-b`.

Example:

```bat
java -Xmx512M -cp target;lib/ECLA.jar;lib/DTNConsoleConnection.jar core.DTNSim -b 5 custom_settings\final_settings.txt
```

This runs the selected simulation **5 times**.

---

### 4. Run Using the Graphical User Interface

If the ONE simulator graphical launcher is available, the simulation can also be started using:

```bat
.\one.bat custom_settings\final_settings.txt
```

Example:

```bat
.\one.bat custom_settings\aco_ga.txt
```

---

## How to Modify Experiments

### Change Number of Nodes

All settings files are configured for **110 nodes by default**.

To use a different number of nodes, edit the node-related values inside the chosen file in:

```text
custom_settings/
```

### Change Message Time To Live

To change the **Time To Live (TTL)** of messages, edit:

```text
Group.msgTtL
```

inside the selected settings file.

---

## Suggested Experiment Flow

A typical comparison workflow is:

1. Run the **baseline Ant Colony Optimisation + Glowworm Swarm Optimisation (ACO + GSO)** router.
2. Run the three pairwise hybrids:
   - **Ant Colony Optimisation + Genetic Algorithm (ACO + GA)**
   - **Ant Colony Optimisation + Particle Swarm Optimisation (ACO + PSO)**
   - **Genetic Algorithm + Particle Swarm Optimisation (GA + PSO)**
3. Run the **final proposed hybrid router**.
4. Compare the generated outputs with the result files in the `metrics` folder.
5. Refer to the plots in the `visualisation` folder for quick comparison.

---

## Summary of Compared Routers

| Router | Type |
|--------|------|
| ACO + GSO | Baseline router proposed by Azzoug |
| ACO + GA | Two-algorithm hybrid |
| ACO + PSO | Two-algorithm hybrid |
| GA + PSO | Two-algorithm hybrid |
| ACO + GA + PSO | Final proposed hybrid |

---

## Reproducibility Notes

- The provided result files are based on **single-run experiments**.
- Default settings are configured for **110 nodes**.
- Both **30-minute** and **60-minute** Time To Live (TTL) scenarios are included.
- The exact output depends on the selected settings file and the router it references.

---

## Baseline Reference

This repository includes the baseline routing approach proposed by **Azzoug**, implemented through:

- `HybridSwarmRouter.java`
- `baseline_30.txt`
- `baseline_60.txt`

This baseline is used as the reference point for evaluating all other hybrid evolutionary routing strategies in the repository.

---

## Intended Use

This repository is intended for students, researchers, and developers who want to:

- understand the codebase,
- run the simulations,
- reproduce the experiments,
- and compare different hybrid routing strategies in the ONE simulator.

A new user can start by:

1. compiling the project,
2. choosing a settings file from `custom_settings`,
3. running the simulation,
4. and comparing the results using the included metrics and visualisations.
