pipeline {
    agent any

    stages {
        stage('Hello') {
            steps {
                sh '''
                mvn clean install
                mvn exec:java -Dexec.mainClass="org.example.Main" -Dexec.args="$TENANT $OPERATION $EMAIL $PASSWORD"
                '''
            }
        }
    }
}