// Follow this setup guide to integrate the Deno language server with your editor:
// https://deno.land/manual/getting-started/setup-your-environment
// This enables autocomplete, go to definition, etc.

import { serve } from "https://deno.land/std@0.177.0/http/server.ts"

console.log("Hello from Functions!")

serve(async (req) => {
  const body = await req.text()
  console.log("Request body:", body)
  return new Response(JSON.stringify({ reply: "Hello World!", body }), {
    headers: { "Content-Type": "application/json" },
  })
})
