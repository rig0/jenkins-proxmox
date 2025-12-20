<div align="center">

# Jenkins Proxmox Library

![Jenkins](https://img.shields.io/badge/Jenkins-D24939?logo=jenkins&logoColor=white)
![CI/CD](https://img.shields.io/badge/CI%2FCD-239120?logo=gitlab&logoColor=white)
![Groovy](https://img.shields.io/badge/Groovy-5a92a7?logo=apachegroovy&logoColor=white)
![Proxmox](https://img.shields.io/badge/Proxmox-E57000?logo=proxmox&logoColor=white)

![Latest Tag](https://img.shields.io/github/v/tag/rig0/jenkins-proxmox?labelColor=222&color=80ff63&label=latest)
[![Code Factor](https://img.shields.io/codefactor/grade/github/rig0/jenkins-proxmox?color=80ff63&labelColor=222)](https://www.codefactor.io/repository/github/rig0/jenkins-proxmox/overview/main)
![Maintained](https://img.shields.io/badge/maintained-yes-80ff63?labelColor=222)
![GitHub last commit](https://img.shields.io/github/last-commit/rig0/jenkins-proxmox?labelColor=222&color=80ff63)


**A Jenkins shared library for interfacing with the Proxmox VE API. This library provides clean, reusable functions for managing VMs, snapshots, and host operations in Jenkins pipelines.**

</div>

## Features

- **VM Management**: Start, stop, shutdown VMs with graceful and forced options
- **Snapshot Operations**: Create, verify, restore, delete, and list snapshots
- **Host Operations**: Shutdown and reboot Proxmox hosts
- **Status Checking**: Get VM and host status information
- **Flexible Configuration**: Use global parameters or instance-based configuration
- **Error Handling**: Built-in error checking and informative logging
- **Parallel Execution Ready**: All functions designed for parallel execution

## Installation

### Method 1: Jenkins Shared Library (Recommended)

1. In Jenkins, go to **Manage Jenkins** > **Configure System**
2. Scroll to **Global Pipeline Libraries**
3. Add a new library with:
   - **Name**: `jenkins-proxmox`
   - **Default version**: `main` (or your preferred branch)
   - **Retrieval method**: Modern SCM
   - **Source Code Management**: Git
   - **Project Repository**: `https://github.com/rig0/jenkins-proxmox`

### Method 2: Direct Repository Reference

In your Jenkinsfile:

```groovy
library identifier: 'jenkins-proxmox@main',
        retriever: modernSCM([
          $class: 'GitSCMSource',
          remote: 'https://github.com/rig0/jenkins-proxmox'
        ])
```

## Quick Start

### Basic Usage

```groovy
@Library('jenkins-proxmox') _

pipeline {
  agent any

  parameters {
    string(name: 'PROXMOX_HOST', defaultValue: 'proxmox.example.com')
    string(name: 'PROXMOX_NODE_NAME', defaultValue: 'pve')
    string(name: 'PROXMOX_API_PORT', defaultValue: '8006')
    string(name: 'PROXMOX_CREDENTIAL_ID', defaultValue: 'proxmox-api-token')
  }

  environment {
    PROXMOX_BASE_URL = "https://${params.PROXMOX_HOST}:${params.PROXMOX_API_PORT}/api2/json/nodes/${params.PROXMOX_NODE_NAME}"
  }

  stages {
    stage('Manage VM') {
      steps {
        script {
          // Start a VM
          proxmoxLib.startVm('100')

          // Create a snapshot
          proxmoxLib.createSnapshot('100', 'pre-update-snapshot')

          // Your work here...

          // Restore snapshot
          proxmoxLib.restoreSnapshot('100', 'pre-update-snapshot')

          // Cleanup
          proxmoxLib.deleteSnapshot('100', 'pre-update-snapshot')
        }
      }
    }
  }
}
```

### Advanced Usage (Instance-Based Configuration)

```groovy
@Library('jenkins-proxmox') _

pipeline {
  agent any

  stages {
    stage('Manage Multiple Proxmox Hosts') {
      steps {
        script {
          // Create instances for different Proxmox hosts
          def prodProxmox = proxmoxLib.create([
            host: 'prod-proxmox.example.com',
            node: 'pve-prod',
            apiPort: '8006',
            credentialId: 'prod-proxmox-token'
          ])

          def testProxmox = proxmoxLib.create([
            host: 'test-proxmox.example.com',
            node: 'pve-test',
            apiPort: '8006',
            credentialId: 'test-proxmox-token'
          ])

          // Use different instances
          prodProxmox.startVm('100')
          testProxmox.startVm('200')
        }
      }
    }
  }
}
```

## API Reference

### VM Management Functions

#### `startVm(vmId, waitSeconds = 30)`
Start a VM and optionally wait for it to boot.

**Parameters:**
- `vmId` (String): VM ID to start
- `waitSeconds` (Integer, optional): Wait time after starting, default: 30

**Example:**
```groovy
proxmoxLib.startVm('100')
proxmoxLib.startVm('100', 60)  // Wait 60 seconds after starting
```

#### `stopVm(vmId, waitSeconds = 5)`
Forcefully stop a VM.

**Parameters:**
- `vmId` (String): VM ID to stop
- `waitSeconds` (Integer, optional): Wait time after stopping, default: 5

**Example:**
```groovy
proxmoxLib.stopVm('100')
```

#### `shutdownVm(vmId, timeout = 60, forceAfterTimeout = true)`
Gracefully shutdown a VM using ACPI shutdown. Optionally force stop if timeout is reached.

**Parameters:**
- `vmId` (String): VM ID to shutdown
- `timeout` (Integer, optional): Timeout in seconds for graceful shutdown, default: 60
- `forceAfterTimeout` (Boolean, optional): Force stop if timeout reached, default: true

**Example:**
```groovy
// Graceful shutdown with 60s timeout, force if needed
proxmoxLib.shutdownVm('100')

// Graceful shutdown with 120s timeout, don't force
proxmoxLib.shutdownVm('100', 120, false)
```

#### `ensureVmRunning(vmId, waitSeconds = 30)`
Ensure a VM is running. Starts the VM if it's not already running.

**Parameters:**
- `vmId` (String): VM ID to check/start
- `waitSeconds` (Integer, optional): Wait time if starting is needed

**Example:**
```groovy
proxmoxLib.ensureVmRunning('100')
```

#### `getVmStatus(vmId)`
Get the current status of a VM.

**Parameters:**
- `vmId` (String): VM ID to check

**Returns:** String (e.g., "running", "stopped")

**Example:**
```groovy
def status = proxmoxLib.getVmStatus('100')
if (status == 'running') {
  echo "VM is running"
}
```

#### `getVmConfig(vmId)`
Get the configuration of a VM.

**Parameters:**
- `vmId` (String): VM ID

**Returns:** JSON response string with VM configuration

**Example:**
```groovy
def config = proxmoxLib.getVmConfig('100')
echo config
```

### Snapshot Functions

#### `createSnapshot(vmId, snapName, includeVmState = 1)`
Create a snapshot of a VM.

**Parameters:**
- `vmId` (String): VM ID to snapshot
- `snapName` (String): Snapshot name
- `includeVmState` (Integer, optional): 1 = include memory, 0 = disk only, default: 1

**Example:**
```groovy
proxmoxLib.createSnapshot('100', 'before-update')
proxmoxLib.createSnapshot('100', 'before-update', 0)  // Disk only
```

#### `verifySnapshotReady(vmId, snapName, retryAttempts = 24, retryInterval = 5)`
Verify a snapshot is ready (not in 'prepare' or 'delete' state).

**Parameters:**
- `vmId` (String): VM ID
- `snapName` (String): Snapshot name
- `retryAttempts` (Integer, optional): Number of retry attempts, default: 24
- `retryInterval` (Integer, optional): Interval between retries in seconds, default: 5

**Example:**
```groovy
proxmoxLib.verifySnapshotReady('100', 'before-update')
```

#### `restoreSnapshot(vmId, snapName, pauseSeconds = 5)`
Restore a VM to a snapshot.

**Parameters:**
- `vmId` (String): VM ID to restore
- `snapName` (String): Snapshot name
- `pauseSeconds` (Integer, optional): Pause after restore, default: 5

**Example:**
```groovy
proxmoxLib.restoreSnapshot('100', 'before-update')
```

#### `deleteSnapshot(vmId, snapName)`
Delete a snapshot from a VM.

**Parameters:**
- `vmId` (String): VM ID
- `snapName` (String): Snapshot name

**Example:**
```groovy
proxmoxLib.deleteSnapshot('100', 'before-update')
```

#### `listSnapshots(vmId)`
List all snapshots for a VM.

**Parameters:**
- `vmId` (String): VM ID

**Returns:** JSON response string with snapshot list

**Example:**
```groovy
def snapshots = proxmoxLib.listSnapshots('100')
echo snapshots
```

#### `snapshotExists(vmId, snapName)`
Check if a snapshot exists for a VM.

**Parameters:**
- `vmId` (String): VM ID
- `snapName` (String): Snapshot name

**Returns:** Boolean (true if exists, false otherwise)

**Example:**
```groovy
if (proxmoxLib.snapshotExists('100', 'backup-snapshot')) {
  echo "Snapshot exists"
} else {
  echo "Snapshot not found"
}
```

#### `safeRestoreSnapshot(vmId, snapName, throwOnError = false)`
Safely restore a VM to a snapshot with error handling. Checks if snapshot exists before attempting restore. Designed for cleanup scenarios where snapshots may not exist.

**Parameters:**
- `vmId` (String): VM ID to restore
- `snapName` (String): Snapshot name
- `throwOnError` (Boolean, optional): If true, re-throws exceptions; if false, logs and continues, default: false

**Returns:** Boolean (true if successful, false if failed when throwOnError is false)

**Example:**
```groovy
// Basic usage - logs errors but continues
proxmoxLib.safeRestoreSnapshot('100', 'backup-snapshot')

// Check result
def success = proxmoxLib.safeRestoreSnapshot('100', 'backup-snapshot')
if (!success) {
  echo "Restore failed or snapshot not found"
}

// Throw on error for critical operations
proxmoxLib.safeRestoreSnapshot('100', 'backup-snapshot', true)
```

**Use Case:**
Perfect for `post.always` blocks where you want to restore snapshots even if they might not exist:
```groovy
post {
  always {
    script {
      // Safely restore even if snapshot creation failed
      proxmoxLib.safeRestoreSnapshot('100', env.SNAPSHOT_NAME)
    }
  }
}
```

#### `safeDeleteSnapshot(vmId, snapName, throwOnError = true)`
Safely delete a snapshot with error handling. Checks if snapshot exists before attempting deletion. Designed for cleanup scenarios where snapshots may not exist.

**Parameters:**
- `vmId` (String): VM ID
- `snapName` (String): Snapshot name
- `throwOnError` (Boolean, optional): If true, re-throws exceptions (default: true for critical cleanup); if false, logs and continues

**Returns:** Boolean (true if successful, false if failed when throwOnError is false)

**Example:**
```groovy
// Critical cleanup - throws on error (default)
proxmoxLib.safeDeleteSnapshot('100', 'backup-snapshot')

// Silent cleanup - logs but doesn't fail
proxmoxLib.safeDeleteSnapshot('100', 'backup-snapshot', false)

// Check result
def deleted = proxmoxLib.safeDeleteSnapshot('100', 'backup-snapshot', false)
if (!deleted) {
  echo "Snapshot deletion failed or snapshot not found"
}
```

**Use Case:**
Perfect for `post.always` blocks to ensure snapshots are cleaned up:
```groovy
post {
  always {
    script {
      // Critical: Always try to delete snapshots
      try {
        proxmoxLib.safeDeleteSnapshot('100', env.SNAPSHOT_NAME)
      } catch (Exception e) {
        echo "⚠️ Manual cleanup may be required"
        currentBuild.result = 'UNSTABLE'
      }
    }
  }
}
```

### Host Management Functions

#### `shutdownHost(delay = 0)`
Shutdown the Proxmox host.

**Parameters:**
- `delay` (Integer, optional): Delay in seconds before shutdown, default: 0

**Example:**
```groovy
// Immediate shutdown
proxmoxLib.shutdownHost()

// Shutdown after 60 seconds
proxmoxLib.shutdownHost(60)
```

#### `rebootHost(delay = 0)`
Reboot the Proxmox host.

**Parameters:**
- `delay` (Integer, optional): Delay in seconds before reboot, default: 0

**Example:**
```groovy
proxmoxLib.rebootHost()
```

#### `getHostStatus()`
Get the status of the Proxmox host.

**Returns:** JSON response string with host status

**Example:**
```groovy
def status = proxmoxLib.getHostStatus()
echo status
```

### Low-Level API Function

#### `api(method, endpoint, data = null)`
Make a raw Proxmox API call with error handling.

**Parameters:**
- `method` (String): HTTP method (GET, POST, DELETE, PUT)
- `endpoint` (String): API endpoint (e.g., "/qemu/100/status/current")
- `data` (String or Map, optional): Form data as string or Map

**Returns:** JSON response string

**Example:**
```groovy
// GET request
def response = proxmoxLib.api('GET', '/qemu/100/status/current')

// POST with data as string
proxmoxLib.api('POST', '/qemu/100/status/start', 'timeout=30')

// POST with data as Map
proxmoxLib.api('POST', '/qemu/100/snapshot', [snapname: 'test', vmstate: 1])
```

#### `proxmoxApi(method, endpoint, data = null)`
Alias for `api()` method for backwards compatibility with existing pipelines.

## Configuration

### Required Jenkins Credentials

Create a Jenkins credential (Secret Text) containing your Proxmox API token:

1. Generate API token in Proxmox:
   ```bash
   pveum user token add jenkins@pve build-automation --privsep=0
   ```

2. In Jenkins, create a **Secret Text** credential:
   - ID: `proxmox-api-token` (or your preferred ID)
   - Secret: `PVEAPIToken=jenkins@pve!build-automation=<your-token-value>`

### Pipeline Parameters

When using the library in backwards compatible mode (without `create()`), define these parameters:

```groovy
parameters {
  string(name: 'PROXMOX_HOST', defaultValue: 'proxmox.example.com', description: 'Proxmox hostname')
  string(name: 'PROXMOX_NODE_NAME', defaultValue: 'pve', description: 'Proxmox node name')
  string(name: 'PROXMOX_API_PORT', defaultValue: '8006', description: 'Proxmox API port')
  string(name: 'PROXMOX_CREDENTIAL_ID', defaultValue: 'proxmox-api-token', description: 'Credential ID for Proxmox API token')

  // Optional: Override defaults for VM operations
  string(name: 'VM_BOOT_WAIT_SECONDS', defaultValue: '30', description: 'Wait time after starting a VM')

  // Optional: Override defaults for snapshot operations
  string(name: 'SNAPSHOT_INCLUDE_VMSTATE', defaultValue: '1', description: '1=include VM memory state, 0=disk only')
  string(name: 'SNAPSHOT_RETRY_ATTEMPTS', defaultValue: '24', description: 'Snapshot readiness check attempts')
  string(name: 'SNAPSHOT_RETRY_INTERVAL_SECONDS', defaultValue: '5', description: 'Interval between readiness checks')

  // Optional: Override defaults for restore operations
  string(name: 'RESTORE_PAUSE_SECONDS', defaultValue: '5', description: 'Pause between VM restore operations')
}

environment {
  PROXMOX_BASE_URL = "https://${params.PROXMOX_HOST}:${params.PROXMOX_API_PORT}/api2/json/nodes/${params.PROXMOX_NODE_NAME}"
}
```

## Complete Examples

### Example 1: VM Snapshot Workflow

```groovy
@Library('jenkins-proxmox') _

pipeline {
  agent any

  parameters {
    string(name: 'PROXMOX_HOST', defaultValue: 'proxmox.example.com')
    string(name: 'PROXMOX_NODE_NAME', defaultValue: 'pve')
    string(name: 'PROXMOX_API_PORT', defaultValue: '8006')
    string(name: 'PROXMOX_CREDENTIAL_ID', defaultValue: 'proxmox-api-token')
    string(name: 'VM_IDS', defaultValue: '100,101,102', description: 'Comma-separated VM IDs')
  }

  environment {
    PROXMOX_BASE_URL = "https://${params.PROXMOX_HOST}:${params.PROXMOX_API_PORT}/api2/json/nodes/${params.PROXMOX_NODE_NAME}"
    SNAPSHOT_NAME = "build-${env.BUILD_NUMBER}"
  }

  stages {
    stage('Ensure VMs Running') {
      steps {
        script {
          def vmList = params.VM_IDS.split(',')

          parallel vmList.collectEntries { vmId ->
            ["VM ${vmId}": {
              proxmoxLib.ensureVmRunning(vmId.trim())
            }]
          }
        }
      }
    }

    stage('Create Snapshots') {
      steps {
        script {
          def vmList = params.VM_IDS.split(',')

          parallel vmList.collectEntries { vmId ->
            ["VM ${vmId}": {
              proxmoxLib.createSnapshot(vmId.trim(), env.SNAPSHOT_NAME)
              proxmoxLib.verifySnapshotReady(vmId.trim(), env.SNAPSHOT_NAME)
            }]
          }
        }
      }
    }

    stage('Run Tests') {
      steps {
        sh 'echo "Running tests against VMs..."'
        // Your test commands here
      }
    }

    stage('Restore Snapshots') {
      steps {
        script {
          def vmList = params.VM_IDS.split(',')

          parallel vmList.collectEntries { vmId ->
            ["VM ${vmId}": {
              proxmoxLib.restoreSnapshot(vmId.trim(), env.SNAPSHOT_NAME)
            }]
          }
        }
      }
    }

    stage('Cleanup Snapshots') {
      steps {
        script {
          def vmList = params.VM_IDS.split(',')

          parallel vmList.collectEntries { vmId ->
            ["VM ${vmId}": {
              proxmoxLib.deleteSnapshot(vmId.trim(), env.SNAPSHOT_NAME)
            }]
          }
        }
      }
    }
  }
}
```

### Example 2: VM Lifecycle Management

```groovy
@Library('jenkins-proxmox') _

pipeline {
  agent any

  stages {
    stage('Setup') {
      steps {
        script {
          def proxmox = proxmoxLib.create([
            host: 'proxmox.example.com',
            node: 'pve',
            apiPort: '8006',
            credentialId: 'proxmox-api-token'
          ])

          // Start VM
          proxmox.startVm('100', 60)

          // Check status
          def status = proxmox.getVmStatus('100')
          echo "VM Status: ${status}"

          // Create snapshot before work
          proxmox.createSnapshot('100', 'before-changes')
          proxmox.verifySnapshotReady('100', 'before-changes')
        }
      }
    }

    stage('Perform Work') {
      steps {
        sh 'echo "Doing work on VM..."'
      }
    }

    stage('Cleanup') {
      steps {
        script {
          def proxmox = proxmoxLib.create([
            host: 'proxmox.example.com',
            node: 'pve',
            apiPort: '8006',
            credentialId: 'proxmox-api-token'
          ])

          // Graceful shutdown
          proxmox.shutdownVm('100', 120)

          // Remove snapshot
          proxmox.deleteSnapshot('100', 'before-changes')
        }
      }
    }
  }
}
```


5. **Error Handling**: Wrap operations in try-catch blocks for production pipelines:
   ```groovy
   try {
     proxmoxLib.createSnapshot('100', 'snapshot-name')
   } catch (Exception e) {
     echo "Snapshot creation failed: ${e.message}"
     currentBuild.result = 'FAILURE'
   }
   ```

6. **Instance-Based Config**: For managing multiple Proxmox hosts, use instance-based configuration:
   ```groovy
   def proxmox1 = proxmoxLib.create([...])
   def proxmox2 = proxmoxLib.create([...])
   ```

## Troubleshooting

### Authentication Errors

If you see authentication errors:
1. Verify the API token format: `PVEAPIToken=user@realm!tokenid=uuid`
2. Check the credential ID matches your Jenkins credential
3. Ensure the token has proper permissions in Proxmox

### Timeout Issues

If operations timeout:
1. Increase wait times in function parameters
2. Check Proxmox host performance and load
3. Verify network connectivity between Jenkins and Proxmox

### Snapshot Verification Fails

If `verifySnapshotReady()` fails:
1. Increase `retryAttempts` parameter
2. Check VM disk performance (snapshots require disk I/O)
3. Verify sufficient storage space on Proxmox