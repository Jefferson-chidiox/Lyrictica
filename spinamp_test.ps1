$body = @{
    query = "{ __type(name: `"ProcessedTrack`") { fields { name } } }"
} | ConvertTo-Json

try {
    $response = Invoke-RestMethod -Uri "https://api.spinamp.xyz/v3/graphql" -Method Post -ContentType "application/json" -Body $body
    $response.data.__type.fields.name -join ", "
} catch {
    $_.Exception.Response.GetResponseStream() | %{ (New-Object IO.StreamReader($_)).ReadToEnd() }
}
