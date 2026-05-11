$body = @{
    username = "superadmin"
    password = "ChangeMe!12345"
} | ConvertTo-Json

$response = Invoke-RestMethod -Uri "http://localhost:8080/api/auth/login" -Method POST -ContentType "application/json" -Body $body
$token = $response.accessToken

$headers = @{
    "Authorization" = "Bearer $token"
    "Content-Type" = "application/json"
}

Write-Host "Testing POST /api/cashier/orders"

$orderBody = @{
    clientNickname = "TestWalkIn"
    clientGender = "F"
    pax = 1
    serviceIds = @(1)
    subtotal = 350
    discount = 0
    total = 350
} | ConvertTo-Json

Write-Host "Request body: $orderBody"

try {
    $webResponse = [System.Net.WebRequest]::Create("http://localhost:8080/api/cashier/orders")
    $webResponse.Method = "POST"
    $webResponse.Headers.Add("Authorization", "Bearer $token")
    $webResponse.ContentType = "application/json"

    $stream = $webResponse.GetRequestStream()
    $bytes = [System.Text.Encoding]::UTF8.GetBytes($orderBody)
    $stream.Write($bytes, 0, $bytes.Length)
    $stream.Close()

    $resp = $webResponse.GetResponse()
    Write-Host "Status: $($resp.StatusCode)"
    $reader = New-Object System.IO.StreamReader($resp.GetResponseStream())
    $result = $reader.ReadToEnd()
    $reader.Close()
    Write-Host "Response: $result"
} catch {
    $err = $_.Exception
    if ($err.Response) {
        Write-Host "Status Code: $($err.Response.StatusCode)"
        Write-Host "Status Description: $($err.Response.StatusDescription)"
        $stream = $err.Response.GetErrorResponseStream()
        $reader = New-Object System.IO.StreamReader($stream)
        $errorText = $reader.ReadToEnd()
        $reader.Close()
        Write-Host "Error Response: $errorText"
    } else {
        Write-Host "Error: $err"
    }
}