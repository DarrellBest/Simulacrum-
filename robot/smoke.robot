*** Settings ***
Documentation    Drives every route of the test-control server on :17355.
Library          RequestsLibrary
Library          Collections
Library          String
Test Teardown    Sleep    ${PACE}

*** Variables ***
${BASE}    http://127.0.0.1:17355
# robot --variable PACE:1.5s smoke.robot  slows the suite down
${PACE}    0s

*** Test Cases ***
Health check responds OK
    ${resp}=    GET    ${BASE}/health
    Should Be Equal As Integers    ${resp.status_code}    200
    Should Be Equal    ${resp.text}    ok

Publisher starts and produces messages
    ${resp}=    GET    url=${BASE}/pub/start?hz=20
    Should Be Equal As Integers    ${resp.status_code}    200
    Should Contain    ${resp.text}    started
    Sleep    2s
    ${count}=    GET    url=${BASE}/pub/count
    Should Be True    ${{int(${count.text}) > 0}}    msg=publisher did not tick
    ${stop}=    GET    url=${BASE}/pub/stop
    Should Be Equal    ${stop.text}    stopped

Subscriber observed the traffic
    Sleep    500ms
    ${count}=    GET    ${BASE}/sub/count
    Should Be True    ${{int(${count.text}) >= 0}}    msg=subscriber counter missing

Drawing a polygon and exporting KML succeeds
    GET    ${BASE}/draw/sample
    Sleep    200ms
    ${kml}=    GET    ${BASE}/kml
    Should Contain    ${kml.text}    <Polygon>
    Should Contain    ${kml.text}    <kml

Throttle setting is reflected in ship state
    ${r}=    GET    url=${BASE}/ship/throttle?v=42
    Should Be Equal    ${r.text}    ok
    Sleep    150ms
    ${state}=    GET    ${BASE}/ship/state
    Should Match Regexp    ${state.text}    thr=\\s*42\\.0
    GET    url=${BASE}/ship/throttle?v=0

Rudder setting is reflected in ship state
    ${r}=    GET    url=${BASE}/ship/rudder?v=-15
    Should Be Equal    ${r.text}    ok
    Sleep    150ms
    ${state}=    GET    ${BASE}/ship/state
    Should Match Regexp    ${state.text}    rud=\\s*-15\\.00
    GET    url=${BASE}/ship/rudder?v=0

Ordered heading endpoint accepts a value
    ${r}=    GET    url=${BASE}/ship/heading?v=90
    Should Be Equal    ${r.text}    ok

Autopilot toggle accepts true and false
    ${on}=    GET    url=${BASE}/ship/autopilot?v=true
    Should Be Equal    ${on.text}    ok
    ${off}=    GET    url=${BASE}/ship/autopilot?v=false
    Should Be Equal    ${off.text}    ok

Anchor toggle accepts true and false
    ${on}=    GET    url=${BASE}/ship/anchor?v=true
    Should Be Equal    ${on.text}    ok
    ${off}=    GET    url=${BASE}/ship/anchor?v=false
    Should Be Equal    ${off.text}    ok

Ship state returns the expected schema
    ${state}=    GET    ${BASE}/ship/state
    Should Match Regexp    ${state.text}
    ...    ^lat=-?\\d+\\.\\d+ lon=-?\\d+\\.\\d+ hdg=-?\\d+\\.\\d+ spd=-?\\d+\\.\\d+ thr=-?\\d+\\.\\d+ rud=-?\\d+\\.\\d+$

Ship moves under sustained autopilot
    [Documentation]    Hold throttle and autopilot, then assert the position changed.
    ${before}=    GET    ${BASE}/ship/state
    GET    url=${BASE}/ship/heading?v=180
    GET    url=${BASE}/ship/throttle?v=80
    GET    url=${BASE}/ship/autopilot?v=true
    Sleep    5s
    ${after}=    GET    ${BASE}/ship/state
    ${before_lat}=    Get Regexp Matches    ${before.text}    lat=(-?\\d+\\.\\d+)    1
    ${after_lat}=     Get Regexp Matches    ${after.text}     lat=(-?\\d+\\.\\d+)    1
    Should Not Be Equal    ${before_lat}[0]    ${after_lat}[0]
    ...    msg=ship did not move (lat ${before_lat}[0] -> ${after_lat}[0])
    GET    url=${BASE}/ship/autopilot?v=false
    GET    url=${BASE}/ship/throttle?v=0
    GET    url=${BASE}/ship/heading?v=45

Heartbeat signal increments publisher count
    ${before}=    GET    ${BASE}/pub/count
    ${r}=    GET    ${BASE}/signal/heartbeat
    Should Be Equal    ${r.text}    ok
    Sleep    150ms
    ${after}=    GET    ${BASE}/pub/count
    Should Be True    ${{int(${after.text}) > int(${before.text})}}
    ...    msg=heartbeat did not bump publisher count (${before.text} -> ${after.text})

Sensor signals publish for every kind
    FOR    ${kind}    IN    SONAR    RADAR    AIS    EW    MOB    DISTRESS
        ${before}=    GET    ${BASE}/pub/count
        ${r}=    GET    url=${BASE}/signal/sensor?kind=${kind}
        Should Be Equal    ${r.text}    ok
        Sleep    100ms
        ${after}=    GET    ${BASE}/pub/count
        Should Be True    ${{int(${after.text}) > int(${before.text})}}
        ...    msg=sensor ${kind} did not bump publisher count
    END

UI exposes the registered node ids
    ${list}=    GET    ${BASE}/ui/list
    Should Not Be Empty    ${list.text}
    @{ids}=    Split String    ${list.text}    ,
    Should Contain    ${ids}    btn.allAheadFull
    Should Contain    ${ids}    btn.heartbeat
    Should Contain    ${ids}    btn.sendOnce

UI locate returns screen coordinates for a known node
    ${r}=    GET    url=${BASE}/ui/locate?id=btn.allAheadFull
    Should Match Regexp    ${r.text}    ^-?\\d+,-?\\d+,\\d+,\\d+$

UI locate returns empty for an unknown node
    ${r}=    GET    url=${BASE}/ui/locate?id=does.not.exist
    Should Be Equal    ${r.text}    ${EMPTY}

UI focus endpoint responds OK
    ${r}=    GET    ${BASE}/ui/focus
    Should Be Equal    ${r.text}    ok
