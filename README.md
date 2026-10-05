# Network Monitor

A high-performance, standalone **Java 17** network availability and performance monitoring application built with native Java networking APIs (`java.net.*`), modern Swing GUI with FlatLaf styling, custom Java 2D vector charts, multithreaded concurrent probing, and incident alerting.

---

## Table of Contents
- [Overview & Architecture](#overview--architecture)
- [Key Features](#key-features)
- [Networking Mechanics](#networking-mechanics)
  - [TCP Connection Monitoring](#tcp-connection-monitoring)
  - [ICMP Reachability & Limitations in Java](#icmpreachability-limitations-in-java)
- [Multithreading Architecture](#multithreading-architecture)
- [Project Structure](#project-structure)
- [Prerequisites & Build](#prerequisites--build)
- [Running the Application](#running-the-application)
- [Configuration Guide (`hosts.properties`)](#configuration-guide-hostsproperties)
- [Dashboard & Features Walkthrough](#dashboard--features-walkthrough)
- [Reports & Auditing](#reports--auditing)
- [Verification & Testing](#verification--testing)

---

## Overview & Architecture

**Network Monitor** continuously monitors the reachability, TCP port availability, and round-trip response time (latency) of remote and local hosts, servers, databases, and microservices.

```
+-------------------------------------------------------------------------+
|                               Swing GUI                                 |
|   Dashboard | LatencyChartPanel | UptimeChartPanel | AddHostDialog     |
+-------------------------------------------------------------------------+
                                   ▲
                                   │ Event & Data Binding (EDT)
                                   ▼
+-------------------------------------------------------------------------+
|                        MonitorScheduler Engine                          |
|  - ScheduledExecutorService (Tick / Interval Coordinator)              |
|  - FixedThreadPool ExecutorService (Concurrent Host Probers)            |
+-------------------------------------------------------------------------+
            │                                             │
            ▼                                             ▼
+-----------------------+                     +-----------------------+
|      HostMonitor      |                     |     ConfigManager     |
| (State Transitions &  |                     |  (config/hosts.prop)  |
|       Alerts)         |                     +-----------------------+
+-----------------------+                                 │
      │           │                                       ▼
      ▼           ▼                           +-----------------------+
+-----------+ +-----------+                   |    ReportExporter     |
|PortMonitor| |PingMonitor|                   |  (CSV & TXT Reports)  |
|(TCP Socket| |(ICMP/Echo |                   +-----------------------+
|  Probe)   | |  Probe)   |                               │
+-----------+ +-----------+                               ▼
      │           │                           +-----------------------+
      ▼           ▼                           |     Logger (Disk      |
  java.net.Socket & InetAddress               |    & UI Listener)     |
                                              +-----------------------+
```

---

## Key Features

1. **Host & Port Monitoring**:
   - Monitor hosts by hostname (`google.com`) or IP address (`192.168.1.10`, `127.0.0.1`).
   - Probe specific TCP service ports (e.g., HTTP `80`, HTTPS `443`, PostgreSQL `5432`, DNS `53`, MySQL `3306`) or ICMP ping only (`port = 0`).
2. **TCP State Classification**:
   - Accurately classifies ports into `OPEN` (successful 3-way handshake), `CLOSED` (`Connection refused`), or `TIMEOUT` (`SocketTimeoutException`).
3. **Latency & Statistics Tracking**:
   - High-precision latency measurement in milliseconds using `System.nanoTime()`.
   - Tracks minimum, maximum, running average, and latest response times.
   - Calculates uptime percentage: `(successfulChecks / totalChecks) * 100.0`.
4. **Rich Interactive Swing Dashboard**:
   - Modern dark theme powered by FlatLaf.
   - Overview metrics banner: Total Hosts, Hosts Online, Hosts Down, Average Latency, Global Uptime.
   - Hosts table with color-coded status badges, port states, latency thresholds, and uptime progress bars.
5. **Real-time Java 2D Vector Charts**:
   - **Latency Over Time**: Antialiased line graph with smooth curve, gradient fill under line, time markers, and average latency guidelines.
   - **Uptime & Incidents**: Circular global uptime gauge, individual host uptime bars, and failure frequency distribution.
6. **Incident Alerting & State Transition Detection**:
   - Visual alerts and file logs when a service transitions from `UP` -> `DOWN`.
   - Automatic recovery logging when a service returns from `DOWN` -> `UP`.
7. **Report Export**:
   - Export audit data to structured **CSV** and formatted **TXT** summary reports.
8. **Configuration Persistence**:
   - Load and save hosts in `config/hosts.properties`. Add or remove hosts on the fly.

---

## Networking Mechanics

### TCP Connection Monitoring

The TCP port monitor uses standard Java socket networking (`java.net.Socket` and `java.net.InetSocketAddress`):

```java
try (Socket socket = new Socket()) {
    socket.setTcpNoDelay(true);
    socket.setSoLinger(false, 0);
    socket.connect(new InetSocketAddress(address, port), timeoutMs);
    // Port is OPEN
} catch (SocketTimeoutException e) {
    // Port state: TIMEOUT
} catch (ConnectException e) {
    // Port state: CLOSED (TCP RST received / Connection Refused)
}
```

- **`Socket.connect(...)`**: Initiates a standard TCP three-way handshake (`SYN` -> `SYN-ACK` -> `ACK`).
- If the remote OS is listening on the port, it responds with `SYN-ACK`, the handshake completes, and the port is marked **OPEN**.
- If the remote OS is running but no process is listening on that port, it returns a `RST` (Reset) packet, throwing `java.net.ConnectException` (`Connection refused`), marked **CLOSED**.
- If a firewall silently drops incoming packets, or the host is offline, no response is received before `timeoutMs`, throwing `java.net.SocketTimeoutException`, marked **TIMEOUT**.

### ICMP/Reachability Limitations in Java

Java provides `java.net.InetAddress.isReachable(timeout)` for ping checks. However, developers must understand its implementation constraints:

1. **Privilege Requirement**:
   - On Linux/macOS and Windows, sending raw ICMP ECHO packets requires elevated privileges (root or administrator / raw socket access).
   - If the JVM is running without elevated privileges, `isReachable()` cannot send raw ICMP packets.
2. **Fallback Behavior**:
   - When ICMP cannot be sent, `isReachable()` falls back to attempting an echo connection to TCP port 7 (`echo` service).
   - Because TCP port 7 is disabled or blocked by default on modern operating systems and routers, non-elevated `isReachable()` calls may report `false` even if the machine is reachable.
3. **Best Practice Solution in Network Monitor**:
   - For real-world servers and services, **TCP port checks** (e.g. ports 80, 443, 22, 53, 3306, 5432) provide deterministic, accurate availability verification that works across all operating systems without requiring root or administrative privileges.
   - For ping-only targets (`port = 0`), Network Monitor calls `InetAddress.isReachable()` with diagnostics.

---

## Multithreading Architecture

Network Monitor utilizes a decoupled, non-blocking concurrent architecture:

```
[ ScheduledExecutorService ] (1 thread)
        │ Periodic interval trigger (every 5 seconds)
        ▼
[ ExecutorService (Worker Pool) ] (Fixed Pool, CPU core count, daemon threads)
   ├── Worker-1: Host A Check (TCP Socket Connect)
   ├── Worker-2: Host B Check (TCP Socket Connect)
   ├── Worker-3: Host C Check (Ping / Echo Probe)
   └── Worker-N: Host N Check (Concurrent)
        │
        ▼ Result Callback
[ SwingUtilities.invokeLater() ] -> Update Swing GUI Table & Vector Charts
```

- **Pacing**: `ScheduledExecutorService` schedules checks with a fixed delay so polling does not drift.
- **Worker Concurrency**: Host checks execute across an `ExecutorService` thread pool. Slower or timing-out connections do not block checks on other hosts.
- **Thread Safety**: Host statistics and latency histories use atomic synchronization and `ConcurrentLinkedDeque`.
- **UI Responsiveness**: Network I/O is never executed on the Swing Event Dispatch Thread (EDT). Results and alert notifications are marshaled to the UI using `SwingUtilities.invokeLater()`.

---

## Project Structure

```
network-monitor/
├── pom.xml                               # Maven project definition (Java 17, FlatLaf, JUnit 5)
├── README.md                             # Comprehensive technical documentation
├── config/
│   └── hosts.properties                  # Monitored hosts configuration file
├── logs/
│   ├── .gitkeep
│   └── monitor.log                       # Live rotating application audit log
├── src/
│   ├── main/java/com/monitor/
│   │   ├── Main.java                     # Main application entry point
│   │   ├── model/
│   │   │   ├── Host.java                 # Host model, uptime % & latency statistics
│   │   │   └── MonitorResult.java        # Single check result & status metadata
│   │   ├── monitor/
│   │   │   ├── PingMonitor.java          # ICMP / Echo reachability checker
│   │   │   ├── PortMonitor.java          # TCP port availability checker
│   │   │   └── HostMonitor.java          # Host orchestrator & transition alert generator
│   │   ├── scheduler/
│   │   │   └── MonitorScheduler.java     # Scheduled multithreaded executor service
│   │   ├── gui/
│   │   │   ├── Dashboard.java            # Main Swing GUI window & KPI cards
│   │   │   ├── LatencyChartPanel.java    # Real-time Java 2D vector latency line chart
│   │   │   ├── UptimeChartPanel.java     # Host uptime bar charts & gauge panel
│   │   │   └── AddHostDialog.java        # Modal dialog to add host with connection test
│   │   ├── report/
│   │   │   └── ReportExporter.java       # CSV and TXT report generator
│   │   └── util/
│   │       ├── ConfigManager.java        # Properties configuration file loader/saver
│   │       └── Logger.java               # Thread-safe disk and UI logger
│   └── test/java/com/monitor/
│       ├── HostTest.java                 # Host validation & uptime calculation tests
│       ├── MonitorResultTest.java        # Result model tests
│       ├── LatencyStatsTest.java         # Latency min/avg/max/history buffer tests
│       ├── ConfigManagerTest.java        # Configuration load/save roundtrip tests
│       ├── PortMonitorTest.java          # Live ServerSocket OPEN/CLOSED port tests
│       ├── ReportExporterTest.java       # CSV & TXT report generation tests
│       └── IntegrationVerificationTest.java # Full lifecycle integration test
```

---

## Prerequisites & Build

### Requirements
- **Java Development Kit (JDK)**: Version 17 or higher
- **Apache Maven**: Version 3.8+

### Build the Project
Clone or open the directory and run:
```bash
mvn clean package
```
This runs the full test suite and builds the standalone shaded fat JAR:
`target/network-monitor-1.0.0.jar`

---

## Running the Application

### 1. Launch with GUI (Default)
Run via Maven:
```bash
mvn exec:java
```
Or execute the packaged fat JAR directly:
```bash
java -jar target/network-monitor-1.0.0.jar
```

### 2. Launch in Headless / CLI Server Mode
For headless environments, Docker containers, or continuous background monitoring:
```bash
java -jar target/network-monitor-1.0.0.jar --cli
```

---

## Configuration Guide (`hosts.properties`)

Hosts are defined in `config/hosts.properties` using standard property entries:

```properties
# Format:
# host.<id>.name=<Friendly Name>
# host.<id>.address=<Hostname or IP Address>
# host.<id>.port=<TCP Port (1-65535) or 0 for ICMP Ping only>
# host.<id>.service=<Service Name>
# host.<id>.interval=<Check Interval in Seconds>
# host.<id>.timeout=<Connection Timeout in Milliseconds>
# host.<id>.enabled=<true|false>

host.1.name=Google Public DNS
host.1.address=8.8.8.8
host.1.port=53
host.1.service=DNS Service
host.1.interval=5
host.1.timeout=2500
host.1.enabled=true

host.2.name=Cloudflare DNS
host.2.address=1.1.1.1
host.2.port=53
host.2.service=DNS Service
host.2.interval=5
host.2.timeout=2500
host.2.enabled=true

host.3.name=Local Dev Web Server
host.3.address=127.0.0.1
host.3.port=8080
host.3.service=HTTP Dev Server
host.3.interval=5
host.3.timeout=1500
host.3.enabled=true
```

You can reload or save configurations directly from the GUI toolbar via **Reload Config** and **Save Config**.

---

## Dashboard & Features Walkthrough

### 1. Summary KPI Cards
- **Total Hosts**: Count of active targets being monitored.
- **Hosts Online (UP)**: Live count of operational services.
- **Hosts Down**: Live count of failing services.
- **Avg Latency**: Global average response time in milliseconds.
- **Overall Uptime**: Mean percentage uptime across all targets.

### 2. Main Tabs
- **Hosts Overview**: Detailed tabular view displaying:
  - Host Name & Service
  - Target Address & Port
  - Live Status (`● UP` / `✖ DOWN`)
  - Port Status (`OPEN` / `CLOSED` / `TIMEOUT`)
  - Response Time (`Latency (ms)`)
  - Min / Avg / Max Latency
  - Last Check Time
  - Failure Count
  - Visual Uptime Percentage Progress Bar
- **Latency Charts**: Dynamic Java 2D vector chart showing latency history over time for any selected host with average threshold guidelines.
- **Uptime & Failures**: Circular global uptime gauge and horizontal uptime comparison bars for each host.
- **Alerts & Incidents**: Chronological incident table showing every downtime (`CRITICAL DOWN`) and service restoration (`RECOVERED`).
- **Live Logs**: Streaming real-time console log viewer with auto-scroll and manual clearing.

### 3. Adding a Host
Click **Add Host...** to open the interactive dialog. You can enter host details and click **Test Connection** to verify socket connectivity before saving.

---

## Reports & Auditing

Network Monitor supports exporting full monitoring metrics:

### CSV Export (`network-report.csv`)
Contains both an aggregated summary of every host and a full audit log of individual checks with timestamps, latency, status, and error messages.

### TXT Export (`network-report.txt`)
A clean, printable ASCII summary report detailing system metrics, host availability, and response statistics.

---

## Verification & Testing

To execute all unit and integration tests:
```bash
mvn test
```

### Test Coverage Highlights:
- **`HostTest`**: Validates configuration parameters, negative port boundaries, blank string handling, and uptime percentage formulas.
- **`MonitorResultTest`**: Asserts result models, status flags, and formatting.
- **`LatencyStatsTest`**: Validates min/avg/max latency calculation across sequential checks and history buffer capping (100 items).
- **`ConfigManagerTest`**: Tests properties parsing, round-trip file saving, and fallback mechanisms.
- **`PortMonitorTest`**: Spins up a local `ServerSocket` on an ephemeral port to verify live `OPEN` port detection and `CLOSED` port rejection.
- **`ReportExporterTest`**: Validates CSV and TXT report generation.
- **`IntegrationVerificationTest`**: Simulates a live service failure (server termination), verifies the `DOWN` alert trigger, restarts the server, verifies the `RECOVERED` alert trigger, and exports audit reports.

---

## License
MIT License. Built with Java 17 and Swing.
