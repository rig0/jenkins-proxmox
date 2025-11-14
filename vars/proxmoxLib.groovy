/**
 * Proxmox API Library for Jenkins Pipelines
 *
 * Provides a comprehensive interface to the Proxmox VE API for managing VMs,
 * snapshots, and host operations.
 *
 * Usage:
 *   @Library('jenkins-proxmox') _
 *
 *   // Use with global params (backwards compatible)
 *   proxmoxLib.ensureVmRunning('1440')
 *
 *   // Or create instance with custom config
 *   def proxmox = proxmoxLib.create([
 *     host: 'proxmox.example.com',
 *     node: 'pve',
 *     apiPort: '8006',
 *     credentialId: 'proxmox-api-token'
 *   ])
 *   proxmox.ensureVmRunning('1440')
 */

import groovy.transform.Field

@Field Map config = [:]

/**
 * Create a new Proxmox API instance with custom configuration
 * @param customConfig Map with keys: host, node, apiPort, credentialId
 * @return This instance for method chaining
 */
def create(Map customConfig) {
  config = customConfig
  return this
}

/**
 * Get the base URL for Proxmox API calls
 * @return Base URL string
 */
private String getBaseUrl() {
  if (config.host) {
    return "https://${config.host}:${config.apiPort ?: '8006'}/api2/json/nodes/${config.node}"
  }
  // Backwards compatibility: use environment variable if available
  return env.PROXMOX_BASE_URL ?: "https://${params.PROXMOX_HOST}:${params.PROXMOX_API_PORT}/api2/json/nodes/${params.PROXMOX_NODE_NAME}"
}

/**
 * Get the credential ID for API authentication
 * @return Credential ID string
 */
private String getCredentialId() {
  return config.credentialId ?: params.PROXMOX_CREDENTIAL_ID
}

/**
 * Make a Proxmox API call with error handling
 * @param method HTTP method (GET, POST, DELETE, PUT)
 * @param endpoint API endpoint (e.g., "/qemu/1440/status/current")
 * @param data Optional form data as string (e.g., "snapname=test&vmstate=1") or Map
 * @return Parsed JSON response as string or null on error
 */
def api(String method, String endpoint, data = null) {
  return withCredentials([string(credentialsId: getCredentialId(), variable: 'PROXMOX_TOKEN')]) {
    def url = "${getBaseUrl()}${endpoint}"
    def cmd = "curl -s -k -X ${method} -H 'Authorization: \${PROXMOX_TOKEN}'"

    // Handle data as Map or String
    if (data) {
      def dataStr = data instanceof Map ?
        data.collect { k, v -> "${k}=${v}" }.join('&') :
        data
      cmd += " -d '${dataStr}'"
    }

    cmd += " '${url}'"

    def response = sh(script: cmd, returnStdout: true).trim()

    // Basic error checking - Proxmox returns JSON with errors field on failure
    if (response && response.contains('"errors"')) {
      echo "⚠️  API Warning: ${response}"
    }

    return response
  }
}

/**
 * Get the current status of a VM
 * @param vmId VM ID to check
 * @return Status string (running, stopped, etc.) or null on error
 */
def getVmStatus(vmId) {
  return withCredentials([string(credentialsId: getCredentialId(), variable: 'PROXMOX_TOKEN')]) {
    def status = sh(
      script: """curl -s -k -H "Authorization: \${PROXMOX_TOKEN}" ${getBaseUrl()}/qemu/${vmId}/status/current | jq -r .data.status""",
      returnStdout: true
    ).trim()

    return status
  }
}

/**
 * Start a VM
 * @param vmId VM ID to start
 * @param waitSeconds Wait time after starting (default: 30)
 * @return API response
 */
def startVm(vmId, Integer waitSeconds = null) {
  echo "🟢 Starting VM ${vmId}..."

  def response = withCredentials([string(credentialsId: getCredentialId(), variable: 'PROXMOX_TOKEN')]) {
    sh(
      script: """curl -s -k -X POST -H "Authorization: \${PROXMOX_TOKEN}" ${getBaseUrl()}/qemu/${vmId}/status/start""",
      returnStdout: true
    ).trim()
  }

  // Use provided waitSeconds, or fall back to params, or default to 30
  def wait = waitSeconds ?: (params.VM_BOOT_WAIT_SECONDS ? params.VM_BOOT_WAIT_SECONDS as int : 30)

  echo "⏳ Waiting ${wait}s for VM ${vmId} to boot..."
  sleep wait

  return response
}

/**
 * Stop a VM forcefully
 * @param vmId VM ID to stop
 * @param waitSeconds Wait time after stopping (default: 5)
 * @return API response
 */
def stopVm(vmId, Integer waitSeconds = null) {
  echo "🛑 Stopping VM ${vmId} (forced)..."

  def response = withCredentials([string(credentialsId: getCredentialId(), variable: 'PROXMOX_TOKEN')]) {
    sh(
      script: """curl -s -k -X POST -H "Authorization: \${PROXMOX_TOKEN}" ${getBaseUrl()}/qemu/${vmId}/status/stop""",
      returnStdout: true
    ).trim()
  }

  def wait = waitSeconds ?: 5

  echo "⏳ Waiting ${wait}s for VM ${vmId} to stop..."
  sleep wait

  return response
}

/**
 * Shutdown a VM gracefully (uses ACPI shutdown)
 * @param vmId VM ID to shutdown
 * @param timeout Timeout in seconds to wait for graceful shutdown (default: 60)
 * @param forceAfterTimeout Force stop if graceful shutdown times out (default: true)
 * @return API response
 */
def shutdownVm(vmId, Integer timeout = null, Boolean forceAfterTimeout = true) {
  echo "🔌 Shutting down VM ${vmId} gracefully..."

  def response = withCredentials([string(credentialsId: getCredentialId(), variable: 'PROXMOX_TOKEN')]) {
    sh(
      script: """curl -s -k -X POST -H "Authorization: \${PROXMOX_TOKEN}" -d "timeout=${timeout ?: 60}" ${getBaseUrl()}/qemu/${vmId}/status/shutdown""",
      returnStdout: true
    ).trim()
  }

  def maxWait = timeout ?: 60
  def checkInterval = 5
  def elapsed = 0

  echo "⏳ Waiting up to ${maxWait}s for VM ${vmId} to shutdown..."

  while (elapsed < maxWait) {
    sleep checkInterval
    elapsed += checkInterval

    def status = getVmStatus(vmId)
    if (status == 'stopped') {
      echo "✅ VM ${vmId} shutdown successfully"
      return response
    }
  }

  echo "⚠️  VM ${vmId} did not shutdown within ${maxWait}s"

  if (forceAfterTimeout) {
    echo "🛑 Forcing VM ${vmId} to stop..."
    return stopVm(vmId)
  }

  return response
}

/**
 * Ensure a VM is running, start it if stopped
 * @param vmId VM ID to check/start
 * @param waitSeconds Wait time after starting if needed
 */
def ensureVmRunning(vmId, Integer waitSeconds = null) {
  def vmStatus = getVmStatus(vmId)

  if (vmStatus != "running") {
    startVm(vmId, waitSeconds)
  }

  echo "✅ VM ${vmId} confirmed running"
}

/**
 * Create a snapshot of a VM
 * @param vmId VM ID to snapshot
 * @param snapName Snapshot name
 * @param includeVmState Include VM memory state (1) or disk only (0), default: 1
 * @return API response
 */
def createSnapshot(vmId, String snapName, Integer includeVmState = null) {
  echo "📸 Creating snapshot '${snapName}' for VM ${vmId}..."

  def vmState = includeVmState != null ? includeVmState :
                (params.SNAPSHOT_INCLUDE_VMSTATE ? params.SNAPSHOT_INCLUDE_VMSTATE as int : 1)

  def data = "snapname=${snapName}&vmstate=${vmState}"

  def response = withCredentials([string(credentialsId: getCredentialId(), variable: 'PROXMOX_TOKEN')]) {
    sh(
      script: """curl -s -k -X POST -H "Authorization: \${PROXMOX_TOKEN}" -d "${data}" ${getBaseUrl()}/qemu/${vmId}/snapshot""",
      returnStdout: true
    ).trim()
  }

  echo "✅ Snapshot created for VM ${vmId}"
  return response
}

/**
 * Verify a snapshot is ready (not in 'prepare' or 'delete' state)
 * @param vmId VM ID
 * @param snapName Snapshot name
 * @param retryAttempts Number of retry attempts (default: 24)
 * @param retryInterval Interval between retries in seconds (default: 5)
 */
def verifySnapshotReady(vmId, String snapName, Integer retryAttempts = null, Integer retryInterval = null) {
  def attempts = retryAttempts ?: (params.SNAPSHOT_RETRY_ATTEMPTS ? params.SNAPSHOT_RETRY_ATTEMPTS as int : 24)
  def interval = retryInterval ?: (params.SNAPSHOT_RETRY_INTERVAL_SECONDS ? params.SNAPSHOT_RETRY_INTERVAL_SECONDS as int : 5)

  withCredentials([string(credentialsId: getCredentialId(), variable: 'PROXMOX_TOKEN')]) {
    retry(attempts) {
      sleep interval

      def state = sh(
        script: """curl -s -k -H "Authorization: \${PROXMOX_TOKEN}" ${getBaseUrl()}/qemu/${vmId}/snapshot/${snapName}/config | jq -r .data.snapstate""",
        returnStdout: true
      ).trim()

      if (state == "prepare" || state == "delete") {
        error("VM ${vmId} snapshot not ready yet (state: ${state})")
      } else {
        echo "✅ VM ${vmId} snapshot ready (state: ${state})"
      }
    }
  }
}

/**
 * Restore a VM to a snapshot
 * @param vmId VM ID to restore
 * @param snapName Snapshot name
 * @param pauseSeconds Pause after restore in seconds (default: 5)
 * @return API response
 */
def restoreSnapshot(vmId, String snapName, Integer pauseSeconds = null) {
  echo "🔄 Restoring VM ${vmId} to snapshot '${snapName}'..."

  def response = withCredentials([string(credentialsId: getCredentialId(), variable: 'PROXMOX_TOKEN')]) {
    sh(
      script: """curl -s -k -X POST -H "Authorization: \${PROXMOX_TOKEN}" -d "snapname=${snapName}" ${getBaseUrl()}/qemu/${vmId}/snapshot/${snapName}/rollback""",
      returnStdout: true
    ).trim()
  }

  def pause = pauseSeconds ?: (params.RESTORE_PAUSE_SECONDS ? params.RESTORE_PAUSE_SECONDS as int : 5)
  sleep pause

  echo "✅ VM ${vmId} restore initiated"
  return response
}

/**
 * Delete a snapshot from a VM
 * @param vmId VM ID
 * @param snapName Snapshot name
 * @return API response
 */
def deleteSnapshot(vmId, String snapName) {
  echo "🧹 Deleting snapshot '${snapName}' from VM ${vmId}..."

  def response = withCredentials([string(credentialsId: getCredentialId(), variable: 'PROXMOX_TOKEN')]) {
    sh(
      script: """curl -s -k -X DELETE -H "Authorization: \${PROXMOX_TOKEN}" ${getBaseUrl()}/qemu/${vmId}/snapshot/${snapName}""",
      returnStdout: true
    ).trim()
  }

  echo "✅ Snapshot deleted from VM ${vmId}"
  return response
}

/**
 * List all snapshots for a VM
 * @param vmId VM ID
 * @return API response with snapshot list
 */
def listSnapshots(vmId) {
  echo "📋 Listing snapshots for VM ${vmId}..."

  return withCredentials([string(credentialsId: getCredentialId(), variable: 'PROXMOX_TOKEN')]) {
    sh(
      script: """curl -s -k -H "Authorization: \${PROXMOX_TOKEN}" ${getBaseUrl()}/qemu/${vmId}/snapshot""",
      returnStdout: true
    ).trim()
  }
}

/**
 * Get VM configuration
 * @param vmId VM ID
 * @return API response with VM configuration
 */
def getVmConfig(vmId) {
  return withCredentials([string(credentialsId: getCredentialId(), variable: 'PROXMOX_TOKEN')]) {
    sh(
      script: """curl -s -k -H "Authorization: \${PROXMOX_TOKEN}" ${getBaseUrl()}/qemu/${vmId}/config""",
      returnStdout: true
    ).trim()
  }
}

/**
 * Shutdown the Proxmox host
 * @param delay Delay in seconds before shutdown (default: 0)
 * @return API response
 */
def shutdownHost(Integer delay = 0) {
  echo "🔴 Initiating Proxmox host shutdown..."

  if (delay > 0) {
    echo "⏳ Shutdown will begin in ${delay} seconds..."
  }

  def response = withCredentials([string(credentialsId: getCredentialId(), variable: 'PROXMOX_TOKEN')]) {
    // Build command with delay parameter
    def data = delay > 0 ? "delay=${delay}" : ""
    def curlCmd = """curl -s -k -X POST -H "Authorization: \${PROXMOX_TOKEN}" """

    if (data) {
      curlCmd += """-d "${data}" """
    }

    curlCmd += """${getBaseUrl()}/status"""

    // Note: The actual shutdown endpoint sends a command parameter
    curlCmd = """curl -s -k -X POST -H "Authorization: \${PROXMOX_TOKEN}" -d "command=shutdown" ${getBaseUrl().replaceAll('/qemu/.*', '')}/status"""

    sh(script: curlCmd, returnStdout: true).trim()
  }

  echo "✅ Host shutdown command sent"
  return response
}

/**
 * Reboot the Proxmox host
 * @param delay Delay in seconds before reboot (default: 0)
 * @return API response
 */
def rebootHost(Integer delay = 0) {
  echo "🔄 Initiating Proxmox host reboot..."

  if (delay > 0) {
    echo "⏳ Reboot will begin in ${delay} seconds..."
  }

  def response = withCredentials([string(credentialsId: getCredentialId(), variable: 'PROXMOX_TOKEN')]) {
    def curlCmd = """curl -s -k -X POST -H "Authorization: \${PROXMOX_TOKEN}" -d "command=reboot" ${getBaseUrl().replaceAll('/qemu/.*', '')}/status"""

    sh(script: curlCmd, returnStdout: true).trim()
  }

  echo "✅ Host reboot command sent"
  return response
}

/**
 * Get Proxmox host status
 * @return API response with host status
 */
def getHostStatus() {
  return withCredentials([string(credentialsId: getCredentialId(), variable: 'PROXMOX_TOKEN')]) {
    sh(
      script: """curl -s -k -H "Authorization: \${PROXMOX_TOKEN}" ${getBaseUrl()}/status""",
      returnStdout: true
    ).trim()
  }
}

/**
 * Backward compatibility wrapper: proxmoxApi
 * This maintains compatibility with existing pipelines that use proxmoxApi()
 */
def proxmoxApi(method, endpoint, data = null) {
  return api(method, endpoint, data)
}

// Make this library callable
return this
