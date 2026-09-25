# Adding a new Robot Framework test

This is a reusable prompt for asking Claude (or any other coding assistant)
to add a new test to the Simulacrum smoke suite. Copy the template below,
edit only the **Task** line to describe what you want tested, and paste the
whole thing into a fresh chat. The rest is durable context that applies to
every future test addition.

## Prompt template

> **Task**
>
> Add a new Robot Framework test to the Simulacrum smoke suite that covers:
> **[describe the behavior you want tested in one or two sentences]**.
>
> **Context**
>
> 1. The suite lives at robot/smoke.robot. All tests drive the application
>    through its in-process HTTP test-control endpoint on port 17355.
> 2. Available endpoints are defined in
>    src/main/java/com/simulacrum/testctl/TestControlServer.java. Their
>    handler implementations are in src/main/java/com/simulacrum/App.java in
>    the inner Handlers class. Read both before writing the test so you know
>    what is already exposed.
> 3. The project-local Python venv is at robot/.venv and Robot Framework
>    plus robotframework-requests are already installed there. If the venv
>    is missing, create it with robot/setup-venv.ps1 (Windows) or
>    bash robot/setup-venv.sh (Linux). Do not install Robot Framework into
>    the system Python.
> 4. The shadow jar is at build/libs/simulacrum-all.jar. If you modify any
>    Java code, rebuild with .\gradlew.bat shadowJar before running the suite.
> 5. Run the suite with .\robot\run-on-windows.ps1 on Windows or
>    bash robot/run-under-xvfb.sh on Linux.
>
> **Requirements**
>
> 1. If the feature you are testing does not yet have a test-control
>    endpoint, add one to TestControlServer.java and wire its handler in
>    App.java. Follow the existing pattern: GET only, plain-text response
>    body, query parameters parsed with the existing parseString, parseBool,
>    and parseDouble helpers. UI mutations must be marshaled onto the
>    JavaFX thread with Platform.runLater.
> 2. Write the test in the same style as the existing ones. Use the
>    RequestsLibrary GET keyword. Prefer Should Be Equal for exact response
>    matches and Should Match Regexp for structured responses. Use a FOR
>    loop when the same assertion is repeated across a set of inputs.
> 3. Where appropriate, verify the side effect rather than only that the
>    endpoint returned ok. Examples: snapshot a counter before and after,
>    or read back ship state and assert the field changed.
> 4. Keep each test self-contained. If the test sets state on the ship or
>    starts a publisher, restore the original state at the end so test
>    order does not matter.
>
> **Acceptance Criteria**
>
> 1. The new test passes when the full suite is run via the appropriate
>    runner.
> 2. All existing tests continue to pass.
> 3. If a new Java endpoint was added, it appears in the runner output as
>    the test that exercises it.
>
> **Deliverables**
>
> 1. The updated robot/smoke.robot.
> 2. Any Java changes required to expose the behavior (TestControlServer.java,
>    App.java, or supporting code).
> 3. A one-line summary of the new test.
> 4. The final runner output showing the suite passing.

## Example fill-in

> **Task**
>
> Add a new Robot Framework test to the Simulacrum smoke suite that covers:
> verifying that the publisher Hz rate actually controls the message
> production rate, by starting the publisher at 5 Hz, sampling the
> publisher count over a 2-second window, and asserting the observed rate
> is within plus or minus 30 percent of the requested rate.
>
> *(Context, Requirements, Acceptance Criteria, and Deliverables sections
> pasted verbatim from above.)*

## Why this format

Splitting the prompt into Task / Context / Requirements / Acceptance
Criteria / Deliverables makes the only part you change each time obvious
(the Task line), forces every request to declare its acceptance bar
instead of burying it in prose, and gives Claude a checklist it can self-
verify against before reporting the work complete.
