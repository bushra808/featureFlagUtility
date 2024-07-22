pipeline {
    agent any

    stages {
        stage('Hello') {
            steps {
                sh '''
                mvn clean install > /dev/null
                mvn exec:java -Dexec.mainClass="org.example.Main" -Dexec.args="$TENANT $OPERATION $EMAIL $PASSWORD"
                '''
            }
        }
    }
}