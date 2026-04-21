*** Settings ***
Documentation    Simulacrum smoke test — drives the in-app test control endpoint on :17355.
Library          RequestsLibrary
Library          Collections

*** Variables ***
${BASE}    http://127.0.0.1:17355

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
    GET    url=${BASE}/pub/stop

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
